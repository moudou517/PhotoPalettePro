package com.example.photopalettepro;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.photopalettepro.data.ExportHistory;
import com.example.photopalettepro.data.ExportHistoryRepository;
import com.example.photopalettepro.databinding.ActivityMainBinding;
import com.example.photopalettepro.databinding.PagePosterBinding;
import com.example.photopalettepro.databinding.PageZineBinding;
import com.example.photopalettepro.helper.ExifInfoManager;
import com.example.photopalettepro.helper.GlassEffectHelper;
import com.example.photopalettepro.helper.ImageProcessingHelper;
import com.example.photopalettepro.helper.ImageSaveHelper;
import com.example.photopalettepro.helper.PopupMenuHelper;
import com.example.photopalettepro.helper.PullRefreshHelper;
import com.example.photopalettepro.helper.TitleLongPressHelper;
import com.example.photopalettepro.helper.UIInteractionHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

/**
 * 主界面 Activity
 *
 * 现在是一个「双模式翻页」结构：
 *   第 1 页 —— 摄影海报（原有 4K 渲染 / EXIF / 调色板功能）
 *   第 2 页 —— Zine 明信片（遵循开源 skill photo-to-zine-postcard，输出 2:3 正反面）
 *
 * 顶部栏与底部栏固定，中间由 ViewPager2 左右翻页切换模式。
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    /** 明信片预览渲染宽度（4:3 横版 → 1080×810，够手机屏幕显示，且省内存） */
    private static final int ZINE_PREVIEW_W = 1080;
    /** 明信片导出渲染宽度（4:3 横版 → 2000×1500），明信片印刷尺寸下 >300dpi */
    private static final int ZINE_EXPORT_W = 2000;

    private ActivityMainBinding binding;
    private PagePosterBinding posterBinding;
    private PageZineBinding zineBinding;

    private Bitmap sourceBitmap;
    private Uri currentImageUri;
    private final ExifInfoManager exifInfoManager = new ExifInfoManager();

    // 助手类
    private TitleLongPressHelper titleLongPressHelper;
    private PullRefreshHelper pullRefreshHelper;
    private PullRefreshHelper zinePullRefreshHelper;
    private ImageSaveHelper imageSaveHelper;

    // 历史记录数据库仓库
    private ExportHistoryRepository historyRepository;

    // 临时缓存当前导出的配置信息，在异步保存成功时写入数据库
    private String pendingOptionsJson = "";
    private String pendingSignText = "";

    // 用于管理导出的高清大图引用，以便在异步保存回调后精准回收
    private Bitmap pendingExportBitmap;

    // ---------- Zine 明信片状态 ----------
    /** 预览用：正面 / 背面 */
    private Bitmap zineFrontBitmap;
    private Bitmap zineBackBitmap;
    /** 当前展示的是否为背面 */
    private boolean zineShowingBack = false;
    /** 0 = 未在保存；1 = 正在保存正面；2 = 正在保存背面 */
    private int zineSaveStage = 0;
    /** 明信片是否需要重新渲染（懒加载：只有真的滑到明信片页才渲染） */
    private boolean zineDirty = true;

    private final ActivityResultLauncher<String> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    // 判断是不是「换了另一张照片」：关系到要不要清掉上一张遗留的创作文案。
                    // 重新导入同一张时不清，避免把用户刚打的字抹掉。
                    boolean switchedPhoto = currentImageUri == null || !currentImageUri.equals(uri);
                    currentImageUri = uri;
                    loadSourceImage(uri, switchedPhoto);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 两个模式页都提前 inflate，避免 ViewPager2 懒加载带来的时序问题
        posterBinding = PagePosterBinding.inflate(getLayoutInflater());
        zineBinding = PageZineBinding.inflate(getLayoutInflater());

        // 初始化数据库仓库
        historyRepository = new ExportHistoryRepository(this);

        setupPager();
        checkPrivacyAgreement();
        initializeHelpers();
        initializeUI();
        initializeListeners();
    }

    // ====================================================================
    //  翻页：模式切换
    // ====================================================================

    private void setupPager() {
        View posterRoot = posterBinding.getRoot();
        View zineRoot = zineBinding.getRoot();
        // 明确让两个页面填满 ViewPager2
        posterRoot.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        zineRoot.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View[] pages = {posterRoot, zineRoot};
        binding.vpModes.setAdapter(new ModePagerAdapter(pages));
        // 只有两页，全部保留，翻页时不会重建视图
        binding.vpModes.setOffscreenPageLimit(2);
        binding.vpModes.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateModeIndicator(position);
                // 懒加载：只有真的滑到明信片页，才做明信片的渲染
                if (position == 1 && zineDirty) {
                    renderZinePreview();
                }
            }
        });
        updateModeIndicator(0);
    }

    private void updateModeIndicator(int position) {
        boolean isZine = position == 1;
        binding.dotMode0.setBackgroundResource(
                isZine ? R.drawable.indicator_dot_off : R.drawable.indicator_dot_on);
        binding.dotMode1.setBackgroundResource(
                isZine ? R.drawable.indicator_dot_on : R.drawable.indicator_dot_off);
        binding.btnExport.setText(isZine ? "保存明信片" : "保存到相册");
    }

    private boolean isZineMode() {
        return binding != null && binding.vpModes.getCurrentItem() == 1;
    }

    /**
     * 取色逻辑/风格变化后调用：标记明信片需要重渲染。
     * 若用户当前就在明信片页则立即渲染，否则等滑过去再渲染，
     * 避免在主页面白白多渲染一张明信片。
     */
    private void markZineDirty() {
        zineDirty = true;
        if (isZineMode()) {
            renderZinePreview();
        }
    }

    /** 固定页面适配器：只承载两个预先创建好的页面 */
    private static class ModePagerAdapter extends RecyclerView.Adapter<ModePagerAdapter.PageHolder> {

        private final View[] pages;

        ModePagerAdapter(View[] pages) {
            this.pages = pages;
        }

        @Override
        public int getItemCount() {
            return pages.length;
        }

        @Override
        public int getItemViewType(int position) {
            // 必须按位置区分类型，否则两页会拿到同一个 View 导致重复 attach
            return position;
        }

        @NonNull
        @Override
        public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new PageHolder(pages[viewType]);
        }

        @Override
        public void onBindViewHolder(@NonNull PageHolder holder, int position) {
            // 页面是固定视图，无需绑定数据
        }

        static class PageHolder extends RecyclerView.ViewHolder {
            PageHolder(View itemView) {
                super(itemView);
            }
        }
    }

    // ====================================================================
    //  初始化
    // ====================================================================

    /**
     * 初始化所有助手类
     */
    private void initializeHelpers() {
        // 图片保存助手
        imageSaveHelper = new ImageSaveHelper(this, new ImageSaveHelper.SaveCallback() {
            @Override
            public void onSuccess(String message) {
                // 确保在主线程处理，且 Activity 未被销毁
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || binding == null) {
                        recyclePendingExport();
                        return;
                    }

                    // ---- Zine 明信片：正面保存完成后接着保存背面 ----
                    if (zineSaveStage == 1) {
                        zineSaveStage = 2;
                        Bitmap back = ZinePostcardRenderer.renderBack(
                                sourceBitmap, zinePalette(), collectZineConfig(), ZINE_EXPORT_W);
                        if (back != null) {
                            showToast("正在保存明信片背面...");
                            imageSaveHelper.saveBitmapToGallery(back);
                        } else {
                            zineSaveStage = 0;
                            showToast("明信片正面已保存（背面生成失败）");
                        }
                        return;
                    }
                    if (zineSaveStage == 2) {
                        zineSaveStage = 0;
                        showToast("明信片正反面已保存到相册");
                        // 明信片保存成功：连同明信片信息一起写入 Room 历史，
                        // 下次导入同一张照片时即可恢复上次填写的内容
                        saveCurrentExportToHistory(message);
                        return;
                    }

                    showToast(message);
                    // 图片成功保存到相册后，将历史记录持久化到 Room 数据库
                    saveCurrentExportToHistory(message);
                    // 异步保存任务安全结束后，及时回收高清大图
                    recyclePendingExport();
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    zineSaveStage = 0;
                    if (isFinishing() || isDestroyed()) {
                        recyclePendingExport();
                        return;
                    }
                    showToast(errorMessage);
                    recyclePendingExport();
                });
            }
        });

        // 标题长按助手
        titleLongPressHelper = new TitleLongPressHelper(this, binding.tvAppTitle);
        titleLongPressHelper.setup();

        // 下拉刷新助手（海报页）
        pullRefreshHelper = new PullRefreshHelper(
                posterBinding.swipeRefreshLayout,
                posterBinding.scrollView,
                this,
                new PullRefreshHelper.RefreshCallback() {
                    @Override
                    public void onRefresh() {
                        processAndRender();
                    }

                    @Override
                    public boolean canRefresh() {
                        return sourceBitmap != null;
                    }
                }
        );
        pullRefreshHelper.setup();

        // 下拉刷新助手（明信片页）—— 与海报页同一套阻尼动画与配色
        zinePullRefreshHelper = new PullRefreshHelper(
                zineBinding.zineSwipeRefresh,
                zineBinding.zineScrollView,
                this,
                new PullRefreshHelper.RefreshCallback() {
                    @Override
                    public void onRefresh() {
                        if (sourceBitmap == null) {
                            showToast("请先导入照片");
                            return;
                        }
                        // 重新按主页面的取色逻辑渲染明信片
                        renderZinePreview();
                        showToast("已按当前取色逻辑重新渲染");
                    }

                    @Override
                    public boolean canRefresh() {
                        return sourceBitmap != null;
                    }
                }
        );
        zinePullRefreshHelper.setup();
    }

    /**
     * UI 初始化
     */
    private void initializeUI() {
        // 海报页：预览版块 + 内部 16:9 图像 + 中间滑动卡片
        UIInteractionHelper.applyRoundedClip(posterBinding.previewContainer, 24f);
        UIInteractionHelper.applyRoundedClip(posterBinding.imgPreview, 16f);
        UIInteractionHelper.applyRoundedClip(posterBinding.configCard, 24f);

        // 明信片页：预览卡片 + 信息卡片
        UIInteractionHelper.applyRoundedClip(zineBinding.zinePreviewCard, 24f);
        UIInteractionHelper.applyRoundedClip(zineBinding.zineConfigCard, 24f);

        binding.getRoot().post(() -> {
            if (isFinishing() || isDestroyed() || binding == null) return;
            UIInteractionHelper.applyPressAnimationBatch(
                    posterBinding.btnGeneratePreview,
                    zineBinding.btnGenerateZine,
                    zineBinding.btnFlipZine,
                    binding.btnImport,
                    binding.btnExport,
                    posterBinding.containerSelectMode,
                    posterBinding.containerSelectStyle
            );
        });
    }

    /**
     * 功能监听初始化
     */
    private void initializeListeners() {
        // ---------------- 海报模式 ----------------
        posterBinding.btnGeneratePreview.setOnClickListener(v -> {
            if (sourceBitmap != null) {
                processAndRender();
            } else {
                showToast("请先导入照片");
            }
        });

        posterBinding.containerSelectMode.setOnClickListener(v -> {
            String[] options = {"默认渲染", "取反差色", "突出原色"};
            new PopupMenuHelper(this, v, options, selectedTitle -> {
                posterBinding.tvCurrentMode.setText(selectedTitle);
                if (sourceBitmap != null) {
                    processAndRender();
                    // 取色逻辑变了：标记明信片待重渲染（滑过去时才真正渲染）
                    markZineDirty();
                }
            }).show();
        });

        posterBinding.containerSelectStyle.setOnClickListener(v -> {
            String[] options = {"默认格式", "马赛克化"};
            new PopupMenuHelper(this, v, options, selectedTitle -> {
                posterBinding.tvCurrentStyle.setText(selectedTitle);
                if (sourceBitmap != null) {
                    processAndRender();
                    markZineDirty();
                }
            }).show();
        });

        // ---------------- Zine 明信片模式 ----------------
        zineBinding.btnGenerateZine.setOnClickListener(v -> {
            if (sourceBitmap != null) {
                renderZinePreview();
                showToast("Zine 明信片已生成");
            } else {
                showToast("请先导入照片");
            }
        });

        zineBinding.btnFlipZine.setOnClickListener(v -> flipZineCard());
        zineBinding.zinePreviewCard.setOnClickListener(v -> flipZineCard());

        // 增强现实开关：切换后标记明信片待重渲染（滑过去时渲染）
        zineBinding.switchRealityAnchor.setOnCheckedChangeListener(
                (buttonView, isChecked) -> markZineDirty());

        // ---------------- 两种模式共用 ----------------
        binding.btnImport.setOnClickListener(v -> {
            try {
                pickImageLauncher.launch("image/*");
            } catch (Exception e) {
                showToast("无法打开系统相册");
            }
        });

        binding.btnExport.setOnClickListener(v -> {
            if (sourceBitmap == null) {
                showToast("请先导入照片");
                return;
            }
            if (isZineMode()) {
                exportZinePostcard();
            } else {
                exportPoster();
            }
        });
    }

    // ====================================================================
    //  Zine 明信片：生成 / 翻面 / 导出
    // ====================================================================

    /**
     * Zine 明信片取色：跟随主页面当前选择的取色逻辑（默认渲染 / 取反差色 / 突出原色），
     * 取该色板的全部 6 个颜色（按权重降序），不砍到 3 个。
     * 这 6 个颜色同时用于明信片的方形色块与主元素的涂色层。
     */
    private List<Integer> zinePalette() {
        if (sourceBitmap == null) return null;
        String mode = posterBinding.tvCurrentMode.getText().toString();
        return ColorExtractor.getTopWeightedColors(sourceBitmap, mode, 6);
    }

    private ZinePostcardConfig collectZineConfig() {
        ZinePostcardConfig cfg = new ZinePostcardConfig();
        cfg.title = zineBinding.etZineTitle.getText().toString().trim();
        cfg.subtitle = zineBinding.etZineSubtitle.getText().toString().trim();
        cfg.location = zineBinding.etZineLocation.getText().toString().trim();
        cfg.date = zineBinding.etZineDate.getText().toString().trim();
        String index = zineBinding.etZineIndex.getText().toString().trim();
        cfg.index = index.isEmpty() ? "01" : index;
        cfg.realityAnchor = zineBinding.switchRealityAnchor.isChecked();
        return cfg;
    }

    /** 把当前明信片输入框内容打包成 JSON，用于持久化到 Room */
    private String buildZineJson() {
        if (zineBinding == null) return "";
        try {
            JSONObject o = new JSONObject();
            o.put("title", zineBinding.etZineTitle.getText().toString());
            o.put("subtitle", zineBinding.etZineSubtitle.getText().toString());
            o.put("location", zineBinding.etZineLocation.getText().toString());
            o.put("date", zineBinding.etZineDate.getText().toString());
            o.put("index", zineBinding.etZineIndex.getText().toString());
            o.put("realityAnchor", zineBinding.switchRealityAnchor.isChecked());
            return o.toString();
        } catch (JSONException e) {
            Log.e(TAG, "打包明信片信息失败", e);
            return "";
        }
    }

    /** 从历史记录恢复明信片输入框（只覆盖记录里确实存在的字段，不误清空） */
    private void applyZineJson(String json) {
        if (zineBinding == null || json == null || json.trim().isEmpty()) return;
        try {
            JSONObject o = new JSONObject(json);
            if (o.has("title")) zineBinding.etZineTitle.setText(o.optString("title", ""));
            if (o.has("subtitle")) zineBinding.etZineSubtitle.setText(o.optString("subtitle", ""));
            if (o.has("location")) zineBinding.etZineLocation.setText(o.optString("location", ""));
            if (o.has("date")) zineBinding.etZineDate.setText(o.optString("date", ""));
            if (o.has("index")) zineBinding.etZineIndex.setText(o.optString("index", ""));
            if (o.has("realityAnchor")) {
                zineBinding.switchRealityAnchor.setChecked(o.optBoolean("realityAnchor", false));
            }
        } catch (JSONException e) {
            Log.e(TAG, "解析明信片信息失败", e);
        }
    }

    /**
     * 渲染明信片正反面预览（4:3 横版 → 1080×810）。
     *
     * 安全性要点：
     * 1. 全程 try/catch —— 渲染失败只提示，绝不让 App 闪退；
     * 2. 先把新位图交给 ImageView，再回收旧位图 ——
     *    否则 View 仍持有旧位图，下一帧绘制时会抛
     *    "Canvas: trying to use a recycled bitmap"；
     * 3. Activity 已销毁 / 未导入照片时直接跳过。
     */
    private void renderZinePreview() {
        if (sourceBitmap == null || sourceBitmap.isRecycled()) {
            return;
        }
        if (isFinishing() || isDestroyed() || binding == null || zineBinding == null) {
            return;
        }

        try {
            List<Integer> palette = zinePalette();
            ZinePostcardConfig cfg = collectZineConfig();

            Bitmap front = ZinePostcardRenderer.renderFront(sourceBitmap, palette, cfg, ZINE_PREVIEW_W);
            Bitmap back = ZinePostcardRenderer.renderBack(sourceBitmap, palette, cfg, ZINE_PREVIEW_W);

            // 先换图，再回收旧图
            Bitmap oldFront = zineFrontBitmap;
            Bitmap oldBack = zineBackBitmap;
            zineFrontBitmap = front;
            zineBackBitmap = back;
            zineShowingBack = false;

            zineBinding.imgZineCard.setRotationY(0f);
            showZineSide();
            zineBinding.tvZineEmptyHint.setVisibility(front == null ? View.VISIBLE : View.GONE);
            updateFlipButtonText();

            recycleQuietly(oldFront);
            recycleQuietly(oldBack);

            zineDirty = false;
        } catch (Throwable t) {
            Log.e(TAG, "renderZinePreview 失败", t);
            showToast("明信片渲染失败：" + t.getMessage());
        }
    }

    private void showZineSide() {
        if (zineBinding == null) return;
        Bitmap bmp = zineShowingBack ? zineBackBitmap : zineFrontBitmap;
        if (bmp != null && bmp.isRecycled()) {
            bmp = null;
        }
        zineBinding.imgZineCard.setImageBitmap(bmp);
    }

    private void updateFlipButtonText() {
        if (zineBinding == null) return;
        zineBinding.btnFlipZine.setText(zineShowingBack ? "翻面查看正面" : "翻面查看背面");
    }

    /** 翻页式卡片翻转：正面 ↔ 背面 */
    private void flipZineCard() {
        if (isFinishing() || isDestroyed() || zineBinding == null) return;
        if (zineFrontBitmap == null || zineBackBitmap == null
                || zineFrontBitmap.isRecycled() || zineBackBitmap.isRecycled()) {
            showToast("请先生成 Zine 明信片");
            return;
        }
        zineShowingBack = !zineShowingBack;

        final View card = zineBinding.imgZineCard;
        card.animate()
                .rotationY(90f)
                .setDuration(140)
                .withEndAction(() -> {
                    // Activity 可能已经在翻转动画期间被销毁，这里必须再挡一次
                    if (isFinishing() || isDestroyed() || zineBinding == null) return;
                    showZineSide();
                    card.setRotationY(-90f);
                    card.animate().rotationY(0f).setDuration(140).start();
                })
                .start();
        updateFlipButtonText();
    }

    /** 导出明信片：先正面，成功后自动接着保存背面 */
    private void exportZinePostcard() {
        if (sourceBitmap == null) {
            showToast("请先导入照片");
            return;
        }
        if (zineSaveStage != 0) {
            showToast("正在保存中，请稍候");
            return;
        }

        showToast("正在保存明信片正面...");
        zineSaveStage = 1;
        try {
            Bitmap front = ZinePostcardRenderer.renderFront(
                    sourceBitmap, zinePalette(), collectZineConfig(), ZINE_EXPORT_W);
            if (front == null) {
                zineSaveStage = 0;
                showToast("明信片生成失败");
                return;
            }
            imageSaveHelper.saveBitmapToGallery(front);
        } catch (Throwable t) {
            zineSaveStage = 0;
            Log.e(TAG, "导出明信片失败", t);
            showToast("明信片生成失败：" + t.getMessage());
        }
    }

    /**
     * 释放明信片预览位图。
     * 释放前先从 ImageView 上摘掉引用，避免 View 继续持有已回收的位图。
     */
    private void recycleZinePreview() {
        if (zineBinding != null) {
            zineBinding.imgZineCard.setImageBitmap(null);
        }
        recycleQuietly(zineFrontBitmap);
        recycleQuietly(zineBackBitmap);
        zineFrontBitmap = null;
        zineBackBitmap = null;
        // 位图没了，下次回到明信片页需要重新渲染
        zineDirty = true;
    }

    private static void recycleQuietly(Bitmap bmp) {
        if (bmp != null && !bmp.isRecycled()) {
            bmp.recycle();
        }
    }

    // ====================================================================
    //  图片加载
    // ====================================================================

    /**
     * 加载源图片
     */
    private void loadSourceImage(Uri uri, boolean switchedPhoto) {
        try {
            int[] dimensions = new int[2];
            long totalPixels = ImageProcessingHelper.readPhotoDimensions(
                    getContentResolver(), uri, dimensions);

            // 如果有旧的图片，及时回收释放内存
            if (sourceBitmap != null && !sourceBitmap.isRecycled()) {
                sourceBitmap.recycle();
            }

            sourceBitmap = ImageProcessingHelper.loadSourceBitmap(
                    getContentResolver(), uri, 4000, 4000);

            if (sourceBitmap != null) {
                Bitmap previewBitmap = ImageProcessingHelper.loadPreviewBitmap(
                        getContentResolver(), uri, 2000, 2000);

                posterBinding.imgPreview.setImageBitmap(previewBitmap);

                // iOS 玻璃效果：将照片铺满全屏作为磨砂背景，并应用真实模糊（API 31+）
                if (binding.bgPhotoBackdrop != null) {
                    binding.bgPhotoBackdrop.setImageBitmap(previewBitmap);
                    GlassEffectHelper.applyBackdropBlur(binding.bgPhotoBackdrop, 26f);
                }

                posterBinding.tvEmptyHint.setVisibility(View.GONE);

                // 换了照片：先清掉上一张遗留的明信片创作文案。
                // 标题／副标题／序号是用户自己写的、不是 EXIF 派生，所以单独清；
                // 若这张照片在本地历史里有记录，随后的 checkAndLoadHistoryConfig
                // 会异步回填它自己上次保存的内容。
                if (switchedPhoto) {
                    resetZineCopyFields();
                }

                // 再填充照片自带的原始 EXIF（含明信片的 DATE / LOCATION）
                autoFillExif(uri);

                // 尝试从本地 Room 数据库读取此图片上一次的导出规格，如果存在则进行覆盖回填
                checkAndLoadHistoryConfig(uri);

                // 先释放上一张照片的明信片预览，再标记为待渲染。
                // 不在这里直接渲染：只导入照片时没必要连明信片一起渲染，
                // 等用户真的滑到明信片页再渲染（懒加载，省时省内存）。
                recycleZinePreview();
                if (isZineMode()) {
                    renderZinePreview();
                }

                if (ImageProcessingHelper.isHighResolution(totalPixels)) {
                    showToast("高清底片已就绪，已优化预览显示");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            showToast("图片处理异常");
        }
    }

    /** 清空明信片里由用户撰写的文案字段（换照片时调用）。 */
    private void resetZineCopyFields() {
        zineBinding.etZineTitle.setText("");
        zineBinding.etZineSubtitle.setText("");
        zineBinding.etZineIndex.setText("01");
    }

    private void autoFillExif(Uri uri) {
        Map<String, String> exif = ExifUtil.getPhotoInfo(this, uri);
        exifInfoManager.updateAll(exif);

        posterBinding.etDevice.setText(exifInfoManager.getDevice());
        posterBinding.etLens.setText(exifInfoManager.getLens());
        posterBinding.etShutter.setText(exifInfoManager.getShutter());
        posterBinding.etAperture.setText(exifInfoManager.getAperture());
        posterBinding.etIso.setText(exifInfoManager.getIso());

        // Zine 明信片：用 EXIF 预填 DATE / LOCATION。
        //
        // 必须「无条件 setText」：这里以前加了「非空才写」的判断，
        // 结果新照片没有 EXIF 日期/地点时分支不执行，
        // 上一张照片留下的文字既没被清空、也没读入新值。
        // 读不到就写空字符串 —— 留空是 skill 明确允许的，编造内容才不允许。
        String exifDate = exif == null ? null : exif.get("date");
        zineBinding.etZineDate.setText(exifDate == null ? "" : exifDate.trim());

        String exifLocation = exif == null ? null : exif.get("location");
        zineBinding.etZineLocation.setText(exifLocation == null ? "" : exifLocation.trim());
    }

    private void updateInfoFromUI() {
        exifInfoManager.setDevice(posterBinding.etDevice.getText().toString());
        exifInfoManager.setLens(posterBinding.etLens.getText().toString());
        exifInfoManager.setShutter(posterBinding.etShutter.getText().toString());
        exifInfoManager.setAperture(posterBinding.etAperture.getText().toString());
        exifInfoManager.setIso(posterBinding.etIso.getText().toString());
        exifInfoManager.setSign(posterBinding.etSign.getText().toString());
        exifInfoManager.setWatermarkEnabled(posterBinding.switchAddWatermark.isChecked());
    }

    private void generatePoster() {
        if (sourceBitmap == null) {
            showToast("请先导入照片");
            return;
        }

        try {
            updateInfoFromUI();
            String selectedMode = posterBinding.tvCurrentMode.getText().toString();
            List<Integer> palette = ColorExtractor.getPaletteByMode(sourceBitmap, selectedMode);
            String style = posterBinding.tvCurrentStyle.getText().toString();

            Bitmap result = PosterRenderer.render(
                    this, sourceBitmap, palette, exifInfoManager.getAll(), style);

            if (result != null) {
                Bitmap previewDisplay = ImageProcessingHelper.createPreviewFromResult(result, 4);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || binding == null) {
                        if (previewDisplay != null && !previewDisplay.isRecycled()) previewDisplay.recycle();
                        return;
                    }
                    posterBinding.imgPreview.setImageBitmap(previewDisplay);
                });

                // 及时回收生成的临时高清图，防止内存溢出
                if (!result.isRecycled()) {
                    result.recycle();
                }
                showToast("渲染完成");
            }
        } catch (Exception e) {
            e.printStackTrace();
            showToast("生成预览失败: " + e.getMessage());
        }
    }

    private void processAndRender() {
        if (sourceBitmap == null) return;
        generatePoster();
    }

    /**
     * 导出海报 - 已全面修复异步保存与内存回收引起的内存泄漏与闪退隐患
     */
    private void exportPoster() {
        if (sourceBitmap == null) {
            showToast("请先导入照片");
            return;
        }

        updateInfoFromUI();

        String selectedMode = posterBinding.tvCurrentMode.getText().toString();
        List<Integer> palette = ColorExtractor.getPaletteByMode(sourceBitmap, selectedMode);
        String style = posterBinding.tvCurrentStyle.getText().toString();

        // 在开始导出前，将当前界面的样式选择拼接成轻量 JSON，并缓存水印签名
        this.pendingOptionsJson = "{\"mode\":\"" + selectedMode + "\",\"style\":\"" + style + "\"}";
        this.pendingSignText = posterBinding.etSign.getText().toString();

        // 如果之前有尚未处理完的导出大图，先安全清理
        recyclePendingExport();

        // 渲染导出的高清大图
        pendingExportBitmap = PosterRenderer.render(
                this, sourceBitmap, palette, exifInfoManager.getAll(), style);

        if (pendingExportBitmap != null) {
            showToast("正在保存到相册...");
            // 将 Bitmap 传入进行异步保存
            imageSaveHelper.saveBitmapToGallery(pendingExportBitmap);
        } else {
            showToast("海报生成失败");
        }
    }

    /**
     * 当 ImageSaveHelper 异步保存图片成功后，在回调处将参数写入 Room 数据库
     */
    private void saveCurrentExportToHistory(String outputPathInfo) {
        if (currentImageUri == null || posterBinding == null) return;

        String originalUriStr = currentImageUri.toString();

        // 获取当前界面上用户可能修改过的最新 EXIF 文本
        String currentDevice = posterBinding.etDevice.getText().toString();
        String currentLens = posterBinding.etLens.getText().toString();
        String currentShutter = posterBinding.etShutter.getText().toString();
        String currentAperture = posterBinding.etAperture.getText().toString();
        String currentIso = posterBinding.etIso.getText().toString();

        // 创建导出历史实体对象（传入新增的 EXIF 参数 + 明信片信息）
        ExportHistory history = new ExportHistory(
                originalUriStr,
                pendingOptionsJson,
                pendingSignText,
                outputPathInfo,
                System.currentTimeMillis(),
                currentDevice,
                currentLens,
                currentShutter,
                currentAperture,
                currentIso,
                buildZineJson()
        );

        // 异步写入数据库
        historyRepository.insertHistory(history, id -> {
            Log.d("PhotoPalettePro", "导出配置及修改后的 EXIF 已记录到 Room 数据库，记录 ID: " + id);
        });
    }

    /**
     * 读取并回填同一张图片的上次导出规格（防御性安全增强版）
     */
    private void checkAndLoadHistoryConfig(Uri imageUri) {
        if (imageUri == null) return;

        historyRepository.getLastConfig(imageUri.toString(), history -> {
            if (history != null) {
                // 查到了历史记录，切换回主线程回填 UI，先做安全防御
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || binding == null) return;

                    // 1. 回填水印签名输入框
                    if (history.sign != null) {
                        posterBinding.etSign.setText(history.sign);
                    }

                    // 2. 回填用户上次修改并保存的 EXIF 信息（增加严密的防御性非空判断）
                    if (history.device != null) posterBinding.etDevice.setText(history.device);
                    if (history.lens != null) posterBinding.etLens.setText(history.lens);
                    if (history.shutter != null) posterBinding.etShutter.setText(history.shutter);
                    if (history.aperture != null) posterBinding.etAperture.setText(history.aperture);
                    if (history.iso != null) posterBinding.etIso.setText(history.iso);

                    // 3. 解析 optionsJson 并恢复渲染模式和风格样式
                    try {
                        String json = history.optionsJson;
                        if (json != null && !json.isEmpty()) {
                            if (json.contains("\"mode\":\"")) {
                                String mode = json.split("\"mode\":\"")[1].split("\"")[0];
                                posterBinding.tvCurrentMode.setText(mode);
                            }
                            if (json.contains("\"style\":\"")) {
                                String style = json.split("\"style\":\"")[1].split("\"")[0];
                                posterBinding.tvCurrentStyle.setText(style);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    // 3.5 恢复上次保存明信片时填写的内容（标题/副标题/地点/日期/序号）
                    applyZineJson(history.zineJson);

                    Toast.makeText(MainActivity.this, "已自动载入该图片上次导出的规格及 EXIF 参数", Toast.LENGTH_SHORT).show();

                    // 4. 重新触发渲染预览（generatePoster 内部本身就会调用 updateInfoFromUI() 获取最新输入框的值）
                    processAndRender();
                    // 取色逻辑被历史记录覆盖了：标记明信片待重渲染
                    markZineDirty();
                });
            }
        });
    }

    /**
     * 安全回收暂存的高清导出图片
     */
    private void recyclePendingExport() {
        if (pendingExportBitmap != null && !pendingExportBitmap.isRecycled()) {
            pendingExportBitmap.recycle();
        }
        pendingExportBitmap = null;
    }

    private void checkPrivacyAgreement() {
        SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        boolean isAgreed = prefs.getBoolean("privacy_agreed", false);

        if (!isAgreed) {
            binding.getRoot().post(this::showPrivacyDialog);
        }
    }

    private void showPrivacyDialog() {
        if (isFinishing() || isDestroyed()) return;
        new PrivacyDialogFragment().show(getSupportFragmentManager(), "PrivacyDialog");
    }

    public static class PrivacyDialogFragment extends DialogFragment {
        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            View dialogView = getLayoutInflater().inflate(R.layout.dialog_privacy, null);
            builder.setView(dialogView);
            builder.setCancelable(false);

            AlertDialog dialog = builder.create();

            dialogView.findViewById(R.id.btnAgree).setOnClickListener(v -> {
                SharedPreferences prefs = getActivity().getSharedPreferences("app_settings", Context.MODE_PRIVATE);
                prefs.edit().putBoolean("privacy_agreed", true).apply();
                dialog.dismiss();
            });

            dialogView.findViewById(R.id.btnReject).setOnClickListener(v -> {
                getActivity().finishAffinity();
            });

            dialog.setOnShowListener(dialogInterface -> {
                if (dialog.getWindow() != null) {
                    dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_bottom_rounded);
                    dialog.getWindow().getAttributes().windowAnimations = android.R.style.Animation_Dialog;
                    dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    dialog.setCanceledOnTouchOutside(false);
                    dialog.setCancelable(false);
                }
            });

            return dialog;
        }
    }

    /**
     * 关键修复：显示 Toast 时强制切回主线程并做 Activity 状态校验
     */
    private void showToast(String message) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * 内存吃紧时主动释放明信片预览位图。
     * 只在不在明信片页时释放——否则会把用户正在看的画面清空；
     * 释放后 zineDirty 会被置位，下次滑回明信片页会重新渲染。
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW && !isZineMode()) {
            recycleZinePreview();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (titleLongPressHelper != null) {
            titleLongPressHelper.cleanup();
        }
        recycleZinePreview();
        recyclePendingExport();
        if (sourceBitmap != null && !sourceBitmap.isRecycled()) {
            sourceBitmap.recycle();
        }
        sourceBitmap = null;
        // 置空两个页面的 binding，防止动画回调/异步回调再触碰已销毁的视图
        posterBinding = null;
        zineBinding = null;
        binding = null;
    }
}
