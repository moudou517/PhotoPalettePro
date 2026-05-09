package com.example.photopalettepro;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;

import com.example.photopalettepro.databinding.ActivityMainBinding;
import com.example.photopalettepro.helper.ExifInfoManager;
import com.example.photopalettepro.helper.ImageProcessingHelper;
import com.example.photopalettepro.helper.ImageSaveHelper;
import com.example.photopalettepro.helper.PopupMenuHelper;
import com.example.photopalettepro.helper.PullRefreshHelper;
import com.example.photopalettepro.helper.TitleLongPressHelper;
import com.example.photopalettepro.helper.UIInteractionHelper;

import java.util.List;

/**
 * 主界面 Activity
 * 修复了导出图片后因线程问题导致的闪退，并优化了内存管理
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private Bitmap sourceBitmap;
    private Uri currentImageUri;
    private final ExifInfoManager exifInfoManager = new ExifInfoManager();

    // 助手类
    private TitleLongPressHelper titleLongPressHelper;
    private PullRefreshHelper pullRefreshHelper;
    private ImageSaveHelper imageSaveHelper;

    private final ActivityResultLauncher<String> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    currentImageUri = uri;
                    loadSourceImage(uri);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        checkPrivacyAgreement();
        initializeHelpers();
        initializeUI();
        initializeListeners();
    }

    /**
     * 初始化所有助手类
     */
    private void initializeHelpers() {
        // 图片保存助手
        imageSaveHelper = new ImageSaveHelper(this, new ImageSaveHelper.SaveCallback() {
            @Override
            public void onSuccess(String message) {
                // 修复：确保在主线程弹出 Toast
                showToast(message);
            }

            @Override
            public void onError(String errorMessage) {
                // 修复：确保在主线程弹出 Toast
                showToast(errorMessage);
            }
        });

        // 标题长按助手
        titleLongPressHelper = new TitleLongPressHelper(this, binding.tvAppTitle);
        titleLongPressHelper.setup();

        // 下拉刷新助手
        pullRefreshHelper = new PullRefreshHelper(
                binding.swipeRefreshLayout,
                binding.scrollView,
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
    }

    /**
     * UI 初始化
     */
    private void initializeUI() {
        UIInteractionHelper.setupPreviewContainerClipping(binding.previewContainer);

        binding.getRoot().post(() -> {
            UIInteractionHelper.applyPressAnimationBatch(
                    binding.btnGeneratePreview,
                    binding.btnImport,
                    binding.btnExport,
                    binding.containerSelectMode,
                    binding.containerSelectStyle
            );
        });
    }

    /**
     * 功能监听初始化
     */
    private void initializeListeners() {
        binding.btnGeneratePreview.setOnClickListener(v -> {
            if (sourceBitmap != null) {
                processAndRender();
            } else {
                showToast("请先导入照片");
            }
        });

        binding.btnImport.setOnClickListener(v -> {
            try {
                pickImageLauncher.launch("image/*");
            } catch (Exception e) {
                showToast("无法打开系统相册");
            }
        });

        binding.btnExport.setOnClickListener(v -> {
            if (sourceBitmap != null) {
                exportPoster();
            } else {
                showToast("请先导入照片");
            }
        });

        binding.containerSelectMode.setOnClickListener(v -> {
            String[] options = {"默认渲染", "取反差色", "突出原色"};
            new PopupMenuHelper(this, v, options, selectedTitle -> {
                binding.tvCurrentMode.setText(selectedTitle);
                if (sourceBitmap != null) processAndRender();
            }).show();
        });

        binding.containerSelectStyle.setOnClickListener(v -> {
            String[] options = {"默认格式", "马赛克化"};
            new PopupMenuHelper(this, v, options, selectedTitle -> {
                binding.tvCurrentStyle.setText(selectedTitle);
                if (sourceBitmap != null) processAndRender();
            }).show();
        });
    }

    /**
     * 加载源图片
     */
    private void loadSourceImage(Uri uri) {
        try {
            int[] dimensions = new int[2];
            long totalPixels = ImageProcessingHelper.readPhotoDimensions(
                    getContentResolver(), uri, dimensions);

            sourceBitmap = ImageProcessingHelper.loadSourceBitmap(
                    getContentResolver(), uri, 4000, 4000);

            if (sourceBitmap != null) {
                Bitmap previewBitmap = ImageProcessingHelper.loadPreviewBitmap(
                        getContentResolver(), uri, 2000, 2000);

                binding.imgPreview.setImageBitmap(previewBitmap);
                if (binding.imgPreviewBlur != null) {
                    binding.imgPreviewBlur.setImageBitmap(previewBitmap);
                }

                binding.tvEmptyHint.setVisibility(View.GONE);
                autoFillExif(uri);

                if (ImageProcessingHelper.isHighResolution(totalPixels)) {
                    showToast("高清底片已就绪，已优化预览显示");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            showToast("图片处理异常");
        }
    }

    private void autoFillExif(Uri uri) {
        exifInfoManager.updateAll(ExifUtil.getPhotoInfo(this, uri));

        binding.etDevice.setText(exifInfoManager.getDevice());
        binding.etLens.setText(exifInfoManager.getLens());
        binding.etShutter.setText(exifInfoManager.getShutter());
        binding.etAperture.setText(exifInfoManager.getAperture());
        binding.etIso.setText(exifInfoManager.getIso());
    }

    private void updateInfoFromUI() {
        exifInfoManager.setDevice(binding.etDevice.getText().toString());
        exifInfoManager.setLens(binding.etLens.getText().toString());
        exifInfoManager.setShutter(binding.etShutter.getText().toString());
        exifInfoManager.setAperture(binding.etAperture.getText().toString());
        exifInfoManager.setIso(binding.etIso.getText().toString());
        exifInfoManager.setSign(binding.etSign.getText().toString());
        exifInfoManager.setWatermarkEnabled(binding.switchAddWatermark.isChecked());
    }

    private void generatePoster() {
        if (sourceBitmap == null) {
            showToast("请先导入照片");
            return;
        }

        try {
            updateInfoFromUI();
            String selectedMode = binding.tvCurrentMode.getText().toString();
            List<Integer> palette = ColorExtractor.getPaletteByMode(sourceBitmap, selectedMode);
            String style = binding.tvCurrentStyle.getText().toString();

            Bitmap result = PosterRenderer.render(
                    this, sourceBitmap, palette, exifInfoManager.getAll(), style);

            Bitmap previewDisplay = ImageProcessingHelper.createPreviewFromResult(result, 4);
            binding.imgPreview.setImageBitmap(previewDisplay);

            // 及时回收生成的临时高清图，防止内存溢出
            if (result != null && !result.isRecycled()) {
                result.recycle();
            }

            showToast("渲染完成");
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
     * 导出海报 - 已修复闪退问题
     */
    private void exportPoster() {
        if (sourceBitmap == null) {
            showToast("请先导入照片");
            return;
        }

        updateInfoFromUI();

        String selectedMode = binding.tvCurrentMode.getText().toString();
        List<Integer> palette = ColorExtractor.getPaletteByMode(sourceBitmap, selectedMode);
        String style = binding.tvCurrentStyle.getText().toString();

        // 渲染导出的高清大图
        Bitmap poster = PosterRenderer.render(
                this, sourceBitmap, palette, exifInfoManager.getAll(), style);

        showToast("正在保存到相册...");

        // 这里的保存是异步的
        imageSaveHelper.saveBitmapToGallery(poster);

        /* 注意：不要在这里立即执行 poster.recycle()，
           因为子线程正在读取这个 bitmap 进行编码保存。
           回收操作建议在 ImageSaveHelper.SaveCallback 的回调中处理，
           或者由 ImageSaveHelper 内部保存完毕后自行处理。*/
    }

    private void checkPrivacyAgreement() {
        SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        boolean isAgreed = prefs.getBoolean("privacy_agreed", false);

        if (!isAgreed) {
            binding.getRoot().post(this::showPrivacyDialog);
        }
    }

    private void showPrivacyDialog() {
        PrivacyDialogFragment dialogFragment = new PrivacyDialogFragment();
        dialogFragment.show(getSupportFragmentManager(), "privacy_dialog");
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
     * 关键修复：显示 Toast 时强制切回主线程
     */
    private void showToast(String message) {
        runOnUiThread(() -> {
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (titleLongPressHelper != null) {
            titleLongPressHelper.cleanup();
        }
        if (sourceBitmap != null && !sourceBitmap.isRecycled()) {
            sourceBitmap.recycle();
        }
    }
}