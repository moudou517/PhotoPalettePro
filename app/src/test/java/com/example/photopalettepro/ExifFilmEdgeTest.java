package com.example.photopalettepro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * 手机照片的「等效焦段」换算。
 *
 * <p>手机 EXIF 里的 {@code FocalLength} 是 5~7mm 的物理焦距，直接印到胶片上会被读成
 * 5mm 超广角。这里校验的是 {@link ExifUtil} 里那套「焦距平面分辨率 → 传感器对角线
 * → 裁切系数 → 等效焦段」的纯计算部分（不碰 Android API，因此可以逐条断言）。
 *
 * <p>用 Robolectric 只是为了拿到 android 类，被测代码本身与框架无关。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ExifFilmEdgeTest {

    /** EXIF 的 FocalPlaneResolutionUnit：2 = 英寸，3 = 厘米。 */
    private static final int UNIT_INCH = 2;
    private static final int UNIT_CM = 3;

    /** 由「传感器物理宽度」反推 EXIF 该填的焦距平面分辨率（像素 / 英寸）。 */
    private static double planeRes(int pixels, double sensorMm) {
        return pixels * 25.4 / sensorMm;
    }

    // ------------------------------------------------------------------
    //  传感器对角线反推
    // ------------------------------------------------------------------

    @Test
    public void recoversFullFrameSensorDiagonal() {
        // 36×24mm 全画幅：对角线应为 43.27mm
        double diagonal = ExifUtil.sensorDiagonalMm(
                planeRes(6000, 36.0), planeRes(4000, 24.0),
                UNIT_INCH, 6000, 4000);

        assertEquals(43.27, diagonal, 0.05);
    }

    @Test
    public void recoversPhoneSensorDiagonal() {
        // 1/2.3" 手机底，约 6.17 × 4.55mm：对角线应为 7.67mm
        double diagonal = ExifUtil.sensorDiagonalMm(
                planeRes(4000, 6.17), planeRes(3000, 4.55),
                UNIT_INCH, 4000, 3000);

        assertEquals(7.67, diagonal, 0.05);
    }

    @Test
    public void centimetersAreConvertedProperly() {
        // 厘米单位必须和英寸给出同一个结果，否则多数安卓机都会算错
        double inInches = ExifUtil.sensorDiagonalMm(
                planeRes(4000, 6.17), planeRes(3000, 4.55), UNIT_INCH, 4000, 3000);
        double inCm = ExifUtil.sensorDiagonalMm(
                planeRes(4000, 6.17) / 25.4 * 10.0, planeRes(3000, 4.55) / 25.4 * 10.0,
                UNIT_CM, 4000, 3000);

        assertEquals(inInches, inCm, 0.02);
    }

    // ------------------------------------------------------------------
    //  等效焦段
    // ------------------------------------------------------------------

    @Test
    public void fullFrameLensKeepsItsFocalLength() {
        // 全画幅裁切系数为 1，50mm 就该是 50mm
        assertEquals(50, ExifUtil.equivalentFocalLength(50.0, 43.266));
    }

    @Test
    public void phoneMainCameraLandsOnTwentyFourMillimeters() {
        // iPhone 15 Pro 主摄：6.86mm 物理焦距，1/1.28" 底（对角线约 12.2mm）
        assertEquals(24, ExifUtil.equivalentFocalLength(6.86, 12.22));
    }

    @Test
    public void tinyPhoneSensorProducesWideAngleEquivalent() {
        // 1/2.3" 底 + 4.25mm 物理焦距 ≈ 24mm 等效
        double diagonal = ExifUtil.sensorDiagonalMm(
                planeRes(4000, 6.17), planeRes(3000, 4.55), UNIT_INCH, 4000, 3000);
        assertEquals(24, ExifUtil.equivalentFocalLength(4.25, diagonal));
    }

    @Test
    public void telephotoPhoneLensIsNotFlattened() {
        // 5× 长焦：19mm 物理焦距 + 同一块小底 → 应该算出长焦的等效值，而不是原样 19
        double diagonal = ExifUtil.sensorDiagonalMm(
                planeRes(4000, 6.17), planeRes(3000, 4.55), UNIT_INCH, 4000, 3000);
        int equivalent = ExifUtil.equivalentFocalLength(19.0, diagonal);

        assertTrue("长焦应被放大到 100mm 以上，实际 " + equivalent, equivalent > 100);
    }

    // ------------------------------------------------------------------
    //  护栏：算不出来就必须返回 0（让胶片回落到装饰文案），绝不猜
    // ------------------------------------------------------------------

    @Test
    public void unknownResolutionUnitIsRefused() {
        // 单位不认识时宁可不算，否则会得出一个离谱的裁切系数
        assertEquals(0, ExifUtil.sensorDiagonalMm(
                planeRes(4000, 6.17), planeRes(3000, 4.55), 1, 4000, 3000), 0.0001);
        assertEquals(0, ExifUtil.sensorDiagonalMm(
                planeRes(4000, 6.17), planeRes(3000, 4.55), 0, 4000, 3000), 0.0001);
    }

    @Test
    public void missingOrNonsenseInputsAreRefused() {
        assertEquals(0, ExifUtil.sensorDiagonalMm(0, 0, UNIT_INCH, 4000, 3000), 0.0001);
        assertEquals(0, ExifUtil.sensorDiagonalMm(100, 100, UNIT_INCH, 0, 0), 0.0001);
        assertEquals(0, ExifUtil.sensorDiagonalMm(-5, 100, UNIT_INCH, 4000, 3000), 0.0001);

        assertEquals(0, ExifUtil.equivalentFocalLength(0, 43.266));
        assertEquals(0, ExifUtil.equivalentFocalLength(50, 0));
        assertEquals(0, ExifUtil.equivalentFocalLength(-3, 43.266));
    }

    @Test
    public void implausibleSensorSizeIsRefused() {
        // 反推出 200mm 的「传感器」只可能是 EXIF 被写坏了，不能拿来算
        assertEquals(0, ExifUtil.sensorDiagonalMm(
                planeRes(4000, 200.0), planeRes(3000, 150.0), UNIT_INCH, 4000, 3000), 0.0001);
        // 反过来，反推出 1mm 的传感器同样不合理
        assertEquals(0, ExifUtil.sensorDiagonalMm(
                planeRes(4000, 1.0), planeRes(3000, 0.75), UNIT_INCH, 4000, 3000), 0.0001);
    }

    @Test
    public void implausibleEquivalentIsRefused() {
        // 0.1mm 的「传感器」会让等效焦段飙到几千毫米
        assertEquals(0, ExifUtil.equivalentFocalLength(50, 0.1));
    }
}
