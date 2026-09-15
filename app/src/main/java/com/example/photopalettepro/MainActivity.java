package com.example.photopalettepro;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.photopalettepro.data.ExportHistory;
import com.example.photopalettepro.data.ExportHistoryRepository;
import com.example.photopalettepro.databinding.ActivityMainBinding;
import com.example.photopalettepro.databinding.PageFilmBinding;
import com.example.photopalettepro.databinding.PagePosterBinding;
import com.example.photopalettepro.databinding.PageZineBinding;
import com.example.photopalettepro.film.FilmStock;
import com.example.photopalettepro.helper.AppDialog;
import com.example.photopalettepro.helper.BackdropDrawable;
import com.example.photopalettepro.helper.CrashLogger;
import com.example.photopalettepro.helper.ExifInfoManager;
import com.example.photopalettepro.helper.GlassEffectHelper;
import com.example.photopalettepro.helper.HapticHelper;
import com.example.photopalettepro.helper.ImageProcessingHelper;
import com.example.photopalettepro.helper.ImageSaveHelper;
import com.example.photopalettepro.helper.LiquidGlassDrawable;
import com.example.photopalettepro.helper.PopupMenuHelper;
import com.example.photopalettepro.helper.PullRefreshHelper;
import com.example.photopalettepro.helper.TitleLongPressHelper;
import com.example.photopalettepro.helper.UiInteractionHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.example.photopalettepro.render.ColorExtractor;
import com.example.photopalettepro.config.FilmBorderConfig;
import com.example.photopalettepro.config.ZinePostcardConfig;
import com.example.photopalettepro.util.ExifUtil;
import com.example.photopalettepro.render.ZinePostcardRenderer;
import com.example.photopalettepro.render.FilmBorderRenderer;
import com.example.photopalettepro.render.PosterRenderer;

/**
 * 主界面 Activity
 *
 * 「三模式翻页」结构，以摄影海报为<b>默认落点</b>，向两侧分开两类功能：
 *   第 1 页（往右滑）—— Zine 明信片，需要继续用主页面的取色逻辑
 *   第 2 页（默认）  —— 摄影海报，原有 4K 渲染 / EXIF / 调色板功能
 *   第 3 页（往左滑）—— 胶片边框，与取色逻辑完全无关的独立工具
 *
 * 顶部栏与底部栏固定，中间由 ViewPager2 左右翻页切换模式。
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    // ---- 页面下标（必须与 setupPager() 里的 pages 数组顺序一致）----

    /** 第 1 页：Zine 明信片（依赖取色逻辑） */
    private static final int PAGE_ZINE = 0;
    /** 第 2 页：摄影海报（默认落点） */
    private static final int PAGE_POSTER = 1;
    /** 第 3 页：胶片边框（不依赖取色逻辑） */
    private static final int PAGE_FILM = 2;

    private static final int PAGE_COUNT = 3;

    /** 明信片预览渲染宽度（4:3 横版 → 1080×810，够手机屏幕显示，且省内存） */
    private static final int ZINE_PREVIEW_W = 1080;
    /** 明信片导出渲染宽度（4:3 横版 → 2000×1500），明信片印刷尺寸下 >300dpi */
    private static final int ZINE_EXPORT_W = 2000;

    /** 胶片边框预览渲染宽度；高度由照片比例、张数与边框档位决定 */
    private static final int FILM_PREVIEW_W = 1080;
    /** 胶片边框导出渲染的基准宽度（格子太小时会按比例放大） */
    private static final int FILM_EXPORT_W = 2000;
    /** 每一格的最小像素宽度：低于它就把整张画布放大，而不是让照片糊掉 */
    private static final int FILM_MIN_FRAME_PX = 620;
    /** 解码冗余：格子尺寸上留一点余量，避免边界发虚 */
    private static final float FILM_DECODE_MARGIN = 1.1f;
    /**
     * 算不出格子尺寸时的兜底像素预算（整批共用）。
     *
     * <p>800 万像素 ≈ 32MB。这里刻意不用「按导出宽度解码」当兜底——
     * 十几张那样子就是几百 MB，一选就崩。
     */
    private static final long FILM_DECODE_PIXEL_BUDGET = 8_000_000L;
    /** 一次最多合成几张 */
    private static final int MAX_FILM_PHOTOS = 18;

    /**
     * 界面预览图的最长边。
     *
     * <p>整屏的磨砂背景和海报页的预览框都够用，又不会把原图整张解出来——
     * 4000×3000 的照片按这个尺寸采样下来是 1600×1200（约 7.7MB），
     * 而不是整张 48MB。
     */
    private static final int PREVIEW_MAX_PX = 1600;

    /** 底片解码尺寸：海报成品是 4K（3840×2160），4000 足够覆盖。 */
    private static final int SOURCE_MAX_PX = 4000;

    /**
     * 海报预览的渲染尺度。
     *
     * <p>0.25 → 960×540，正好是原来「渲 4K 再缩 4 倍」的结果，
     * 但像素只有 1/16。版面由 {@link PosterRenderer} 里的 Canvas 矩阵等比缩下去，
     * 与成品一致。
     */
    private static final float POSTER_PREVIEW_SCALE = 0.25f;

    private ActivityMainBinding binding;
    private PagePosterBinding posterBinding;
    private PageZineBinding zineBinding;
    private PageFilmBinding filmBinding;

    private Bitmap sourceBitmap;
    private Uri currentImageUri;
    private final ExifInfoManager exifInfoManager = new ExifInfoManager();

    // 助手类
    private TitleLongPressHelper titleLongPressHelper;
    private PullRefreshHelper pullRefreshHelper;
    private PullRefreshHelper zinePullRefreshHelper;
    private PullRefreshHelper filmPullRefreshHelper;
    private ImageSaveHelper imageSaveHelper;

    // 历史记录数据库仓库
    private ExportHistoryRepository historyRepository;

    // 临时缓存当前导出的配置信息，在异步保存成功时写入数据库
    private String pendingOptionsJson = "";
    private String pendingSignText = "";

    // 用于管理导出的高清大图引用，以便在异步保存回调后精准回收
    private Bitmap pendingExportBitmap;

    /** 液态玻璃的表面们（卡片 + 上下浮栏） */
    /** 锁页时允许拖动到的最大翻页进度：三分之一。越过它就弹回并提示。 */
    private static final float LOCKED_SWIPE_LIMIT = 1f / 3f;

    /**
     * 弹回是否已就绪。
     *
     * <p>防止一次拖动里反复触发：越过阈值弹回一次之后置为 false，
     * 等回到允许范围内才重新武装。
     */
    private boolean lockBounceArmed = true;

    /**
     * 这次拖动已经越过三分之一，松手后要把页面拽回来。
     *
     * <p>拖动途中手指还在屏幕上，改 currentItem 是没用的——必须等到松手。
     */
    private boolean lockBouncePending = false;

    private final List<LiquidGlassDrawable> glassSurfaces = new ArrayList<>();

    /** 背景快照：玻璃折射的就是它，换照片时要重拍 */
    private Bitmap backdropSnapshot;

    /**
     * 胶片照片的解码线程。
     *
     * <p>用<b>单线程</b>排队，而不是每次 {@code new Thread}：两次导入撞在一起时
     * （第一次还在解十几张，用户又从底部「导入」选了一张），并发解码会让内存直接翻倍——
     * 这正是「导入进程有冲突」那个画面。配上代次取消，两批照片就绝不会同时占着内存。
     */
    private final ExecutorService filmDecoder = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "film-load");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });

    /** 海报页当前显示的那张预览图；换新图时负责回收旧的 */
    private Bitmap posterPreviewBitmap;

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

    // ---------- 胶片边框状态 ----------
    /** 预览用的胶片边框成品（尺寸随照片比例、张数与边框档位变化） */
    private Bitmap filmPreviewBitmap;
    /** 胶片边框是否需要重新渲染（同样是懒加载） */
    private boolean filmDirty = true;
    /**
     * 本次保存是否由胶片边框页发起。
     *
     * <p>用来决定导出历史要记的是哪一套规格：胶片导出时把胶片配置写进
     * {@code filmJson}，海报导出时写 {@code optionsJson}——两者都存在同一行上，
     * 再次导入这张照片时各取所需。
     */
    private boolean pendingFilmExport = false;

    /**
     * 胶片页的照片集合。
     *
     * <p>与 {@link #sourceBitmap} 分开管理：主照片要按 4K 解码给海报用，
     * 胶片里的每一格则只需要「成品里格子那么大」——十几张按 4K 解码就是几百 MB。
     */
    private final List<Uri> filmUris = new ArrayList<>();
    /** 每张胶片照片的原始像素尺寸 {宽, 高}；只读边界，不解码 */
    private final List<int[]> filmSizes = new ArrayList<>();
    /** 按格子尺寸解码后的照片；大小与 {@link #filmUris} 一致才算有效 */
    private final List<Bitmap> filmBitmaps = new ArrayList<>();
    /** 当前照片集合的指纹，用来判断「是不是换了一批」 */
    private String filmPhotoKey = "";
    /**
     * 后台读取胶片照片的代次。
     *
     * <p>用户在读取途中又选了一批时，先回来的那批结果凭这个作废并回收。
     * {@code volatile}：解码线程每一步都要读它来判断「还要不要继续」。
     */
    private volatile int filmLoadGeneration = 0;
    /** 后台是否正在读取 / 解码胶片照片 */
    private boolean filmLoadPending = false;
    /** 翻页是否被锁住（多张合成期间） */
    private boolean pagerLocked = false;
    /** 上一次「停稳」的页面，用来决定换页震动要不要发 */
    private int lastSettledPage = PAGE_POSTER;
    /** 「多张时翻页被锁」的说明弹窗；持有引用是为了不重复叠窗、并在销毁时收掉 */
    private android.app.Dialog multiPhotoLockDialog;

    private final ActivityResultLauncher<String> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    onMainPhotoPicked(uri);
                }
            }
    );

    /**
     * 底部「导入」选完照片后的全部处理。
     *
     * <p>从 launcher 回调里抽出来有两个好处：回调只剩一行适配；
     * 而这条路径是崩溃高发区，抽成包内可见之后就能被测试直接驱动
     * （见 {@code MainActivityImportTest}）。
     */
    void onMainPhotoPicked(Uri uri) {
        if (uri == null) return;
        CrashLogger.breadcrumb("onMainPhotoPicked " + uri);

        boolean switchedPhoto = currentImageUri == null || !currentImageUri.equals(uri);
        currentImageUri = uri;
        loadSourceImage(uri, switchedPhoto);
        // 底部「导入」是单张语义，胶片页也只剩这一张
        applyFilmPhotoSet(Collections.singletonList(uri));
    }

    /**
     * 胶片页专用的多选导入。
     *
     * <p>与底部的单张导入分开：海报要的是「一张底片出一个 4K 成品」，
     * 胶片则是「一组照片拼成一条片」，两者的语义本来就不一样。
     */
    private final ActivityResultLauncher<String> pickFilmPhotosLauncher = registerForActivityResult(
            new ActivityResultContracts.GetMultipleContents(),
            uris -> {
                if (uris != null && !uris.isEmpty()) {
                    onFilmPhotosPicked(uris);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 开会话：重置上一场的记录，并挂上崩溃兜底。
        // 3.0 起不再把「上一场没正常结束」弹给用户——那是开发期的东西，
        // 对普通用户只是一句看不懂的报错。记录仍然照写，需要时自己去取。
        CrashLogger.beginSession(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 系统栏透明之后，inset 得自己处理。
        //
        // ⚠️ 注意：setOnApplyWindowInsetsListener 会【覆盖】fitsSystemWindows 的默认处理，
        // 所以这里必须把两者都做掉——之前只对齐了底部外边距，
        // 结果根布局不再给状态栏让位，标题直接顶到状态栏文字上了。
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            androidx.core.graphics.Insets bars =
                    insets.getInsets(WindowInsetsCompat.Type.systemBars());

            // 顶部：让开状态栏。
            //
            // 「让开」不等于「把状态栏高度整段当间距」——那样标题离状态栏会比
            // 它离预览卡还远，看着头重脚轻。所以标题自己那一档间距动态算：
            // 取状态栏高度的四分之一，并且【不超过标题模块到预览模块的距离】，
            // 于是两种间距谁大谁小永远是一致的。
            int titleGap = Math.min(
                    Math.round(12 * getResources().getDisplayMetrics().density),
                    Math.round(bars.top / 4f));
            v.setPadding(bars.left,
                    Math.max(0, bars.top - Math.round(12 * getResources().getDisplayMetrics().density)
                            + titleGap),
                    bars.right, 0);

            // 底部：模块和小白条的距离 = 小白条到屏幕底部的距离
            ViewGroup.MarginLayoutParams params =
                    (ViewGroup.MarginLayoutParams) binding.bottomGlassBar.getLayoutParams();
            if (params.bottomMargin != bars.bottom) {
                params.bottomMargin = bars.bottom;
                binding.bottomGlassBar.setLayoutParams(params);
            }

            // 页码胶囊浮在滑块之上，位置 = 底栏上沿再抬一点。
            // 它和底栏是两块独立的模块，间距按底栏实际高度算，不写死——
            // 底部按钮高度或系统栏一变，这里跟着走。
            v.post(() -> {
                ViewGroup.MarginLayoutParams indicator =
                        (ViewGroup.MarginLayoutParams) binding.pageIndicator.getLayoutParams();
                int gap = Math.round(14 * getResources().getDisplayMetrics().density);
                int target = bars.bottom + binding.bottomGlassBar.getHeight() + gap;
                if (indicator.bottomMargin != target) {
                    indicator.bottomMargin = target;
                    binding.pageIndicator.setLayoutParams(indicator);
                }
            });
            return insets;
        });

        // 铺底色：渐变 + 色雾。
        // 色雾不是装饰——玻璃本身不着色，卡片上的颜色全部来自它背后被折射的东西，
        // 背后什么都没有时，半透明卡片看上去就是一块白板（见 BackdropDrawable）。
        binding.getRoot().setBackground(new BackdropDrawable(this));

        // 三个模式页都提前 inflate，避免 ViewPager2 懒加载带来的时序问题
        posterBinding = PagePosterBinding.inflate(getLayoutInflater());
        zineBinding = PageZineBinding.inflate(getLayoutInflater());
        filmBinding = PageFilmBinding.inflate(getLayoutInflater());

        // 初始化数据库仓库
        historyRepository = new ExportHistoryRepository(this);

        setupPager();
        checkPrivacyAgreement();
        initializeHelpers();
        initializeUI();
        initializeListeners();
    }

    @Override
    protected void onStart() {
        super.onStart();
        CrashLogger.onResumed();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // 退到后台之后被系统回收是很常见的事，不是 bug —— 记一笔，
        // 下次启动时就不会把它当成「异常结束」来弹窗
        CrashLogger.onStopped();
    }

    // ====================================================================
    //  翻页：模式切换
    // ====================================================================

    private void setupPager() {
        View zineRoot = zineBinding.getRoot();
        View posterRoot = posterBinding.getRoot();
        View filmRoot = filmBinding.getRoot();
        // 明确让每一页都填满 ViewPager2
        zineRoot.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        posterRoot.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        filmRoot.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 顺序决定左右两侧各是什么：下标越大越靠「左滑」方向
        View[] pages = {zineRoot, posterRoot, filmRoot};
        binding.vpModes.setAdapter(new ModePagerAdapter(pages));
        // 只有三页，全部保留，翻页时不会重建视图
        binding.vpModes.setOffscreenPageLimit(PAGE_COUNT - 1);

        // 回调先注册、再设初始页：这样「落在取色页」这件事只有一个出处，
        // 指示点与底部按钮文案都由 onPageSelected 统一驱动，不会出现
        // 代码改了初始页、指示点却还停在旧位置的两套状态。
        binding.vpModes.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                // 指示点跟着拖动实时走，用户才有「翻到哪了」的反馈
                updateModeIndicator(position);
            }

            @Override
            public void onPageScrolled(int position, float offset, int offsetPixels) {
                // 锁页期间盯住拖动进度：越过三分之一就弹回来（见 watchLockedScroll）
                watchLockedScroll(position, offset);
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                // 松手那一刻（SETTLING）是唯一能真正决定落点的时机。
                // 在这之前 setCurrentItem 会被正在进行的拖动覆盖掉。
                if (state == ViewPager2.SCROLL_STATE_SETTLING) {
                    settleLockedPage();
                }
                // 只有「完全滑进某一页」才做懒渲染。
                //
                // onPageSelected 在拖动刚过半就会触发，那时手指还在屏幕上，
                // 明信片那种要跑边缘检测 + 线稿重绘的渲染会直接把滑动卡住。
                // 所以渲染放到 SCROLL_STATE_IDLE —— 页面真的停稳了才开始。
                if (state != ViewPager2.SCROLL_STATE_IDLE || binding == null) return;

                int position = binding.vpModes.getCurrentItem();
                if (position != lastSettledPage) {
                    lastSettledPage = position;
                    HapticHelper.pageChanged(binding.vpModes);
                }
                resetLeftBehindScrolls(position);
                renderSettledPage(position);
            }
        });

        // 进 App 默认落在第 2 页——取色逻辑的主页面（摄影海报）。
        // 往左滑是胶片边框（与取色无关），往右滑是明信片（继续用取色逻辑）。
        binding.vpModes.setCurrentItem(PAGE_POSTER, false);
        // 首帧可能早于 onPageSelected 回调，先同步一次指示点兜底
        updateModeIndicator(PAGE_POSTER);

        // 多张胶片合成期间翻页会被锁死，这时得靠外层容器告诉我们「用户想翻页」。
        // 此处还没有照片，setPagerLocked(false) 会把监听摘掉、翻页保持可用。
        setPagerLocked(false);
    }

    /**
     * 页面停稳后的懒渲染。
     *
     * <p>三条都很贵，所以只在「已经站在这一页上」时才做：
     * 明信片要跑边缘检测 + 线稿重绘，胶片要解十几张照片，
     * 海报的 4K 渲染更是又慢又占内存。
     */
    private void renderSettledPage(int position) {
        if (position == PAGE_ZINE) {
            if (zineDirty) renderZinePreview();
        } else if (position == PAGE_FILM) {
            // 解码结果在离开这一页时可能已被回收，滑回来按需补一次
            ensureFilmPhotosDecoded();
            if (filmDirty) renderFilmPreview();
        }
    }

    /**
     * 翻页停稳后，把<b>刚离开的那几页</b>的滑块复位到顶部。
     *
     * <p>不这么做的话：在某一页把配置滑到一半，翻走再翻回来，
     * 它还停在原来那个位置——用户得自己往回找，而且回来第一眼看到的是
     * 半截版式，不知道自己在哪。
     *
     * <p>复位的是「非当前页」：正在看的那一页当然要保持用户刚滑到的地方。
     * 三页都是常驻的（{@code setOffscreenPageLimit(2)}），所以这里直接改
     * 另外两页不会有任何视觉跳动——它们本来就在屏幕外。
     */
    private void resetLeftBehindScrolls(int currentPosition) {
        if (currentPosition != PAGE_POSTER) posterBinding.scrollView.scrollTo(0, 0);
        if (currentPosition != PAGE_ZINE) zineBinding.zineScrollView.scrollTo(0, 0);
        if (currentPosition != PAGE_FILM) filmBinding.filmScrollView.scrollTo(0, 0);
    }

    /**
     * 多张合成时【限制】左右翻页，而不是彻底锁死。
     *
     * <p>海报的取色与明信片的渲染都只针对<b>单张</b>照片：色板要从一张图上聚类，
     * 明信片的版式也只放得下一张。选着多张的时候翻过去，那两页要么算的是
     * 「第一张」而用户以为是全部，要么干脆没意义——所以不能真的放过去。
     *
     * <p>但「一动就弹窗」太粗暴：手指刚划一点就跳出对话框，正常操作也会误触。
     * 现在改成<b>可以像平时那样拖，只是拖不过三分之一</b>：
     * <ul>
     *   <li>没拖过 1/3 就松手 —— 和平时一样回弹，<b>什么都不提示</b>；</li>
     *   <li>拖过 1/3 —— 立刻弹回胶片页，给一下急促的震动，再解释为什么；</li>
     * </ul>
     * 于是「想翻页」和「手滑蹭到」被区分开了。
     */
    private void setPagerLocked(boolean locked) {
        if (binding == null || pagerLocked == locked) return;
        pagerLocked = locked;
        // 触摸始终可用：要的就是"能拖但拖不过去"，
        // 彻底关掉 userInput 就变回"一动就弹窗"了。
        binding.vpModes.setUserInputEnabled(true);
        binding.swipeWatch.setSwipeAttemptListener(null);
        if (!locked) {
            lockBounceArmed = true;
        }
    }

    /**
     * 拖动过程中盯住「离胶片页走了多远」，越过三分之一就弹回来。
     *
     * <h3>不要用 position 判方向</h3>
     *
     * <p>第一版写的是 {@code if (position != PAGE_FILM && offset > 0f) return;}，
     * 结果限制完全没生效。原因是 ViewPager2 的 {@code onPageScrolled} 在<b>往回翻</b>时
     * {@code position} 报的是<b>目标页</b>：从胶片页（2）滑向海报页（1）时它报
     * {@code position = 1}、{@code offset} 从 1 递减到 0——那句判断每次都对不上，
     * 于是每次都在第一行返回。
     *
     * <p>改用 {@code position + offset} 这个<b>连续的虚拟页位置</b>：
     * 从 2 滑向 1 时它从 2.0 走到 1.0，所以「离胶片页多远」就是 {@code |2 - 它|}，
     * 单调、和方向无关。三分之一那道坎直接落在这个量上。
     */
    /**
     * 离胶片页走了多远：0 = 还在胶片页，1 = 已经完全翻到隔壁。
     *
     * <p>抽成纯函数是为了能直接验——这个 bug（用 {@code position} 判方向、
     * 结果限制完全没生效）正是单元测试能一眼抓住的那类。
     */
    static float swipeAwayFromFilm(int position, float offset) {
        return Math.abs(PAGE_FILM - (position + offset));
    }

    private void watchLockedScroll(int position, float offset) {
        if (!pagerLocked || binding == null) return;
        // 只有「本来就停在胶片页」才拦。否则多张刚选完那一瞬，
        // 停在别的页上的正常滑动会被误判成"想翻走"。
        if (lastSettledPage != PAGE_FILM) return;

        // 0 = 还在胶片页，1 = 已经完全翻到隔壁
        float away = swipeAwayFromFilm(position, offset);

        if (away < LOCKED_SWIPE_LIMIT) {
            // 还在允许范围内：松手会自己回弹，不需要我们做任何事
            lockBounceArmed = true;
            return;
        }

        if (!lockBounceArmed) return;
        lockBounceArmed = false;
        lockBouncePending = true;

        HapticHelper.blockedByLock(binding.vpModes);
        showMultiPhotoLockDialog();
        blockAndBounceBack();
    }

    /**
     * 一次完成「掐断这次手势 → 弹回胶片页 → 放开触控」。
     *
     * <h3>三个坑，各踩过一次</h3>
     *
     * <p><b>一、{@code setUserInputEnabled(false)} 拦不住已经开始的那次拖动。</b>
     * 它只影响"下一次"手势；手指还按着的这一次，RecyclerView 的拖拽状态照样有效，
     * 页面就卡在两页之间——用户看到的正是这个。
     * 所以这里主动给 ViewPager2 派一个 {@code ACTION_CANCEL}，
     * 让它当这次手势已经结束：拖拽状态清掉，页面才肯回去。
     *
     * <p><b>二、不能在 {@code onPageScrolled} 的调用栈里改 currentItem。</b>
     * 那时 ViewPager2 正在派发自己的滚动事件，改目标页会被忽略或打架。
     * 所以用 {@code post} 推到下一帧，跳出这个调用栈再做。
     *
     * <p><b>三、用瞬时定位而不是动画。</b>动画可以被后续事件打断，
     * 一旦被打断就回到了"卡住"；瞬时定位没有中间态，一定到位。
     */
    private void blockAndBounceBack() {
        if (binding == null) return;

        // 1) 掐断正在进行的手势
        long now = android.os.SystemClock.uptimeMillis();
        MotionEvent cancel = MotionEvent.obtain(now, now,
                MotionEvent.ACTION_CANCEL, 0f, 0f, 0);
        binding.vpModes.dispatchTouchEvent(cancel);
        cancel.recycle();

        // 2) 这一次之后的手势也不再接受
        binding.vpModes.setUserInputEnabled(false);

        // 3) 跳出 onPageScrolled 的调用栈再回位
        binding.vpModes.post(() -> {
            if (binding == null) return;
            binding.vpModes.setCurrentItem(PAGE_FILM, false);
            lockBouncePending = false;

            binding.vpModes.postDelayed(() -> {
                if (!pagerLocked || binding == null) return;
                binding.vpModes.setUserInputEnabled(true);
                lockBounceArmed = true;
            }, 260);
        });
    }

    /**
     * 松手之后兜住：锁页期间只要落点不是胶片页，就拽回来。
     *
     * <p>和 {@link #blockAndBounceBack()} 是同一个动作的两条触发路径：
     * 一条是拖动途中越过三分之一，一条是松手后发现落点不对。谁先到谁做。
     */
    private void settleLockedPage() {
        if (!pagerLocked || binding == null) return;

        boolean wrongPage = binding.vpModes.getCurrentItem() != PAGE_FILM;
        if (!wrongPage && !lockBouncePending) return;

        blockAndBounceBack();
    }

    /**
     * 用户试图横向翻页（SwipeWatchLayout 的旁观回调）。
     *
     * <p>{@link #setPagerLocked(boolean)} 之后不再监听它——现在触摸是放开的，
     * 拖动本身由 {@link #watchLockedScroll} 接管。留这个方法是给
     * 布局还在用老回调的情况兜底。
     */
    private void onHorizontalSwipeAttempt() {
        if (!pagerLocked) return;
        showMultiPhotoLockDialog();
    }

    private void showMultiPhotoLockDialog() {
        if (isFinishing() || isDestroyed() || binding == null) return;
        if (multiPhotoLockDialog != null && multiPhotoLockDialog.isShowing()) return;

        // 用应用自己的对话框，而不是系统的 AlertDialog——
        // 系统那套是一张方白纸，摆在一堆玻璃卡片中间很割裂。
        // 这里和选择弹窗共用同一块面板素材（bg_popup_panel），
        // 读起来就是"一块带确认按钮的模块"。
        multiPhotoLockDialog = AppDialog.show(this,
                "已选多张照片",
                "海报的取色和明信片的渲染都只针对单张照片，"
                        + "所以选入多张时这两页暂时用不了，左右翻页也已锁定。\n\n"
                        + "想用它们，请回到胶片页把照片减到 1 张（重新点一次"
                        + "「选择照片」只选一张），或者用底部的「导入」重新选一张。",
                "知道了", null);
    }

    private void updateModeIndicator(int position) {
        binding.dotMode0.setBackgroundResource(
                position == PAGE_ZINE ? R.drawable.indicator_dot_on : R.drawable.indicator_dot_off);
        binding.dotMode1.setBackgroundResource(
                position == PAGE_POSTER ? R.drawable.indicator_dot_on : R.drawable.indicator_dot_off);
        binding.dotMode2.setBackgroundResource(
                position == PAGE_FILM ? R.drawable.indicator_dot_on : R.drawable.indicator_dot_off);

        if (position == PAGE_ZINE) {
            binding.btnExport.setText("保存明信片");
        } else if (position == PAGE_FILM) {
            binding.btnExport.setText("保存胶片边框");
        } else {
            binding.btnExport.setText("保存到相册");
        }
    }

    /** 当前是否停在某一页（Activity 未就绪时一律算「不在」）。 */
    private boolean isPage(int page) {
        return binding != null && binding.vpModes.getCurrentItem() == page;
    }

    private boolean isZineMode() {
        return isPage(PAGE_ZINE);
    }

    private boolean isFilmMode() {
        return isPage(PAGE_FILM);
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

    /** 固定页面适配器：只承载三个预先创建好的页面 */
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
            // 必须按位置区分类型，否则各页会拿到同一个 View 导致重复 attach
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
                    // 保存失败也要把这次导出的「归属」清掉，
                    // 否则下一次海报导出会被错记成胶片导出
                    pendingFilmExport = false;
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
                        return hasPhoto();
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
                        if (!hasPhoto()) {
                            showToast("请先导入照片");
                            return;
                        }
                        // 重新按主页面的取色逻辑渲染明信片
                        renderZinePreview();
                        showToast("已按当前取色逻辑重新渲染");
                    }

                    @Override
                    public boolean canRefresh() {
                        return hasPhoto();
                    }
                }
        );
        zinePullRefreshHelper.setup();

        // 下拉刷新助手（胶片边框页）—— 同样一套阻尼动画与配色
        filmPullRefreshHelper = new PullRefreshHelper(
                filmBinding.filmSwipeRefresh,
                filmBinding.filmScrollView,
                this,
                new PullRefreshHelper.RefreshCallback() {
                    @Override
                    public void onRefresh() {
                        if (!hasPhoto()) {
                            showToast("请先导入照片");
                            return;
                        }
                        renderFilmPreview();
                        showToast("已按当前边框设置重新渲染");
                    }

                    @Override
                    public boolean canRefresh() {
                        return hasPhoto();
                    }
                }
        );
        filmPullRefreshHelper.setup();
    }

    /**
     * 取色逻辑的可选项。
     *
     * <p>{@code key} 是写进历史记录、喂给 {@link ColorExtractor} 的<b>逻辑标识，不能改</b>；
     * {@code title} / {@code summary} 只是给人看的，随便改。
     *
     * <p>以前这两件事是同一个字符串，于是「默认渲染」这种不知所云的名字
     * 既<b>改不动</b>（一改就没有分支接得住，历史记录里存的老值也会失配），
     * 也<b>没法解释</b>它到底做什么。拆开之后名字随便起，逻辑一个字不用动。
     */
    private static final List<PopupMenuHelper.Option> MODE_OPTIONS = Arrays.asList(
            new PopupMenuHelper.Option("默认渲染", "标准取色", "聚类取色，最稳，适合大多数照片"),
            new PopupMenuHelper.Option("取反差色", "反差色", "拉开色相差，配色更跳、更有海报感"),
            new PopupMenuHelper.Option("突出原色", "原色还原", "按像素占比取样，最接近照片本来的颜色"));

    /** 排列方式的可选项。key 同样不能改。 */
    private static final List<PopupMenuHelper.Option> STYLE_OPTIONS = Arrays.asList(
            new PopupMenuHelper.Option("默认格式", "常规排版", "色块按色相排序，克制、耐看"),
            new PopupMenuHelper.Option("马赛克化", "马赛克色块", "把照片切成色块网格，色感更强"));

    /**
     * 胶片风格。
     *
     * <p>说明里那句「台面」不是修辞：片基是半透明的，
     * 每个预设连它摆在哪张台面上都是挑过的，选「暗夜黑」时背景会跟着变亮
     * （近黑片基只有压在灯箱上才看得见）。不写出来用户会以为界面出错了。
     */
    private static final List<PopupMenuHelper.Option> FILM_STYLE_OPTIONS = Arrays.asList(
            new PopupMenuHelper.Option(FilmStock.STYLE_CLASSIC, "经典白框", "暖白片基，橙字，最常见"),
            new PopupMenuHelper.Option(FilmStock.STYLE_KODAK, "柯达金", "偏金的纸白，日光感"),
            new PopupMenuHelper.Option(FilmStock.STYLE_FUJI, "富士绿", "冷绿白，日系清新"),
            new PopupMenuHelper.Option(FilmStock.STYLE_NOIR, "暗夜黑", "近黑片基，台面会跟着变亮"),
            new PopupMenuHelper.Option(FilmStock.STYLE_SILVER, "银盐黑白", "中性灰，黑白负片"));

    /** 胶片边框宽度。名字本身已经说清楚了，不需要额外说明。 */
    private static final List<PopupMenuHelper.Option> POPUP_FILM_WIDTH_OPTIONS = Arrays.asList(
            PopupMenuHelper.Option.plain(FilmBorderConfig.WIDTH_NARROW),
            PopupMenuHelper.Option.plain(FilmBorderConfig.WIDTH_NORMAL),
            PopupMenuHelper.Option.plain(FilmBorderConfig.WIDTH_WIDE));

    // ====================================================================
    //  液态玻璃
    // ====================================================================

    /**
     * 给所有玻璃表面装上 {@link LiquidGlassDrawable}，并拍一张背景快照。
     *
     * <p>为什么要在代码里换掉 XML 背景：液态玻璃的<b>折射</b>需要知道
     * "卡片下面压着什么"，XML 里的静态 drawable 拿不到这个信息。
     */
    private void installLiquidGlass() {
        final float radiusDp = 24f;
        int tint = ContextCompat.getColor(this, R.color.glass_liquid_tint);
        int highlight = ContextCompat.getColor(this, R.color.glass_highlight);
        int border = ContextCompat.getColor(this, R.color.glass_border);

        View[] surfaces = {
                posterBinding.previewContainer,
                posterBinding.configCard,
                zineBinding.zinePreviewCard,
                zineBinding.zineConfigCard,
                filmBinding.filmPreviewCard,
                filmBinding.filmConfigCard,
                binding.topGlassBar,
                binding.bottomGlassBar,
        };

        for (View surface : surfaces) {
            LiquidGlassDrawable glass =
                    new LiquidGlassDrawable(this, radiusDp, tint, highlight, border);
            surface.setBackground(glass);
            glassSurfaces.add(glass);
        }

        refreshBackdropSnapshot();
    }

    /**
     * 重新拍背景快照并分发下去。
     *
     * <p>换了照片就要重拍：玻璃折的是它，不更新的话卡片里透出来的
     * 还是上一张照片的颜色。
     */
    private void refreshBackdropSnapshot() {
        if (glassSurfaces.isEmpty() || binding == null) return;

        View root = binding.getRoot();
        int w = root.getWidth();
        int h = root.getHeight();
        if (w <= 0 || h <= 0) {
            // 还没量出来：等一帧再来
            root.post(this::refreshBackdropSnapshot);
            return;
        }

        // 1/6 尺寸：省内存，放大回来自带柔化
        int snapshotW = Math.max(8, w / 6);
        int snapshotH = Math.max(8, h / 6);

        Bitmap photoSource = posterPreviewBitmap != null && !posterPreviewBitmap.isRecycled()
                ? posterPreviewBitmap : null;

        Bitmap snapshot;
        try {
            snapshot = GlassEffectHelper.buildBackdropSnapshot(
                    snapshotW, snapshotH, new BackdropDrawable(this), photoSource, 0.72f);
        } catch (Throwable t) {
            Log.e(TAG, "背景快照生成失败", t);
            return;
        }
        if (snapshot == null) return;

        Bitmap previous = backdropSnapshot;
        backdropSnapshot = snapshot;
        for (LiquidGlassDrawable glass : glassSurfaces) {
            glass.setBackdrop(snapshot);
        }
        if (previous != null && !previous.isRecycled() && previous != snapshot) {
            previous.recycle();
        }
    }

    /**
     * 用户手上有没有照片。
     *
     * <p>两个坑都在这一行里，各踩过一次：
     *
     * <p><b>一、不能用 {@code sourceBitmap != null}。</b>底片是懒解码的
     * （见 {@link #ensureSourceBitmap()}），刚导入时它还是 null——
     * 拿位图当「有没有照片」的标志，导入后的第一次操作全会误报「请先导入照片」。
     *
     * <p><b>二、也不能只看 {@code currentImageUri}。</b>多张胶片合成时，
     * 「主照片」是被<b>刻意放掉</b>的（那两页已被锁住，见
     * {@link #setPagerLocked(boolean)}），{@code currentImageUri} 置空。
     * 但那时胶片页明明有十几张照片——只看它就等于把「有照片」判成「没照片」，
     * <b>导出按钮会拒绝工作</b>。这正是「多张胶卷生成好后无法导出」的原因。
     *
     * <p>所以这里只回答「有没有东西可导出」；至于「能不能渲出海报」，
     * 由 {@link #ensureSourceBitmap()} 单独判断——两者是不同的门槛。
     */
    private boolean hasPhoto() {
        return currentImageUri != null || !filmUris.isEmpty();
    }

    // ---- 取色逻辑 / 排列方式：key 与显示名分开 ----

    /** 当前取色逻辑的逻辑标识。存在容器的 tag 上，界面显示的是 title。 */
    private String currentModeKey() {
        return tagKey(posterBinding.containerSelectMode, MODE_OPTIONS.get(0).key);
    }

    /** 当前排列方式的逻辑标识。 */
    private String currentStyleKey() {
        return tagKey(posterBinding.containerSelectStyle, STYLE_OPTIONS.get(0).key);
    }

    private static String tagKey(View container, String fallback) {
        Object tag = container == null ? null : container.getTag();
        return tag instanceof String ? (String) tag : fallback;
    }

    /** 落定取色逻辑：tag 存 key，界面显示 title。 */
    private void applyMode(PopupMenuHelper.Option option) {
        posterBinding.containerSelectMode.setTag(option.key);
        posterBinding.tvCurrentMode.setText(option.title);
    }

    /** 落定排列方式。 */
    private void applyStyle(PopupMenuHelper.Option option) {
        posterBinding.containerSelectStyle.setTag(option.key);
        posterBinding.tvCurrentStyle.setText(option.title);
    }

    /**
     * 按逻辑标识设置显示名。
     *
     * <p>从历史记录恢复时用的是 {@code key}（当年存进去的就是它），
     * 但界面上要显示对应的人话——找不到就原样显示，至少不会显示空白。
     */
    private void applyModeByKey(String key) {
        for (PopupMenuHelper.Option option : MODE_OPTIONS) {
            if (option.key.equals(key)) {
                applyMode(option);
                return;
            }
        }
        posterBinding.containerSelectMode.setTag(key);
        posterBinding.tvCurrentMode.setText(key);
    }

    private void applyStyleByKey(String key) {
        for (PopupMenuHelper.Option option : STYLE_OPTIONS) {
            if (option.key.equals(key)) {
                applyStyle(option);
                return;
            }
        }
        posterBinding.containerSelectStyle.setTag(key);
        posterBinding.tvCurrentStyle.setText(key);
    }

    /**
     * UI 初始化
     */
    private void initializeUI() {
        // 只有真正的图片内容需要圆角裁剪。
        //
        // 卡片一律【不】裁剪：clipToOutline 会把整张卡片丢进一个离屏层再合成，
        // 而卡片的玻璃面本身是半透明的——真机 GPU 上这一层合成出来的颜色
        // 和卡片内边距那一圈对不上，于是中间内容区亮、四周一圈偏灰，
        // 看起来就是「白色没有把内容包进去」。
        // 卡片的圆角由背景 drawable 自己负责，不需要再裁一次。
        UiInteractionHelper.applyRoundedClip(posterBinding.imgPreview, 16f);
        UiInteractionHelper.applyRoundedClip(zineBinding.imgZineCard, 16f);
        UiInteractionHelper.applyRoundedClip(filmBinding.imgFilmPreview, 20f);

        installLiquidGlass();

        binding.getRoot().post(() -> {
            if (isFinishing() || isDestroyed() || binding == null) return;
            UiInteractionHelper.applyPressAnimationBatch(
                    posterBinding.btnGeneratePreview,
                    zineBinding.btnGenerateZine,
                    zineBinding.btnFlipZine,
                    filmBinding.btnGenerateFilm,
                    filmBinding.btnPickFilmPhotos,
                    binding.btnImport,
                    binding.btnExport,
                    posterBinding.containerSelectMode,
                    posterBinding.containerSelectStyle,
                    filmBinding.containerSelectFilmStyle,
                    filmBinding.containerSelectFilmWidth
            );
        });
    }

    /**
     * 功能监听初始化
     */
    private void initializeListeners() {
        // ---------------- 海报模式 ----------------
        posterBinding.btnGeneratePreview.setOnClickListener(v -> {
            if (hasPhoto()) {
                processAndRender();
            } else {
                showToast("请先导入照片");
            }
        });

        posterBinding.containerSelectMode.setOnClickListener(v -> {
            new PopupMenuHelper(this, posterBinding.containerSelectMode, MODE_OPTIONS,
                    currentModeKey(), option -> {
                applyMode(option);
                if (hasPhoto()) {
                    processAndRender();
                    // 取色逻辑变了：标记明信片待重渲染（滑过去时才真正渲染）
                    markZineDirty();
                }
            }).show();
        });

        posterBinding.containerSelectStyle.setOnClickListener(v -> {
            new PopupMenuHelper(this, posterBinding.containerSelectStyle, STYLE_OPTIONS,
                    currentStyleKey(), option -> {
                applyStyle(option);
                if (hasPhoto()) {
                    processAndRender();
                    markZineDirty();
                }
            }).show();
        });

        // 初始显示：key 存在容器的 tag 上，界面上显示的是给人看的 title
        applyMode(MODE_OPTIONS.get(0));
        applyStyle(STYLE_OPTIONS.get(0));

        // ---------------- Zine 明信片模式 ----------------
        zineBinding.btnGenerateZine.setOnClickListener(v -> {
            if (hasPhoto()) {
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

        // ---------------- 胶片边框模式 ----------------

        filmBinding.btnPickFilmPhotos.setOnClickListener(v -> {
            if (filmLoadPending) {
                // 上一批还在解：不要让这一下石沉大海（那看起来就是卡死）
                showToast("正在处理上一批照片，请稍候");
                return;
            }
            try {
                pickFilmPhotosLauncher.launch("image/*");
            } catch (Exception e) {
                showToast("无法打开系统相册");
            }
        });

        filmBinding.btnGenerateFilm.setOnClickListener(v -> {
            if (filmUris.isEmpty()) {
                showToast("请先选择照片");
                return;
            }
            renderFilmPreview();
            showToast("胶片边框已生成");
        });

        // 胶片风格：五个预设的名字本来就够清楚（经典白框 / 柯达金 / …），
        // 但每一项仍给一句说明——用户未必知道「暗夜黑」会连台面一起变亮。
        filmBinding.containerSelectFilmStyle.setOnClickListener(v ->
                new PopupMenuHelper(this, v, FILM_STYLE_OPTIONS,
                        filmBinding.tvFilmStyle.getText().toString(),
                        option -> {
                            filmBinding.tvFilmStyle.setText(option.title);
                            markFilmDirty();
                        }).show());

        filmBinding.containerSelectFilmWidth.setOnClickListener(v ->
                new PopupMenuHelper(this, v, POPUP_FILM_WIDTH_OPTIONS,
                        filmBinding.tvFilmWidth.getText().toString(),
                        option -> {
                            filmBinding.tvFilmWidth.setText(option.title);
                            markFilmDirty();
                        }).show());

        // 齿孔 / 帧号是几何与文字上的开关，改完必须重渲染
        filmBinding.switchFilmCaption.setOnCheckedChangeListener(
                (buttonView, isChecked) -> markFilmDirty());
        filmBinding.switchFilmFrameNumber.setOnCheckedChangeListener(
                (buttonView, isChecked) -> markFilmDirty());

        // ---------------- 三种模式共用 ----------------
        binding.btnImport.setOnClickListener(v -> {
            try {
                pickImageLauncher.launch("image/*");
            } catch (Exception e) {
                showToast("无法打开系统相册");
            }
        });

        binding.btnExport.setOnClickListener(v -> {
            if (!hasPhoto()) {
                showToast("请先导入照片");
                return;
            }
            if (isZineMode()) {
                exportZinePostcard();
            } else if (isFilmMode()) {
                exportFilmBorder();
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
        String mode = currentModeKey();
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
     * 渲染明信片预览（4:3 横版 → 1080×810）。
     *
     * <p><b>只渲正面。</b>正面要跑边缘检测 + 线稿重绘，已经是最贵的一步；
     * 背面同样重，而用户不一定翻面，所以留到真翻面时再渲（见 {@link #ensureZineBack()}）。
     * 导出时两边都会渲（{@link #exportZinePostcard()}），不受影响。
     *
     * <p>安全性要点：
     * 1. 全程 try/catch —— 渲染失败只提示，绝不让 App 闪退；
     * 2. 先把新位图交给 ImageView，再回收旧位图 ——
     *    否则 View 仍持有旧位图，下一帧绘制时会抛
     *    "Canvas: trying to use a recycled bitmap"；
     * 3. Activity 已销毁 / 未导入照片时直接跳过。
     */
    private void renderZinePreview() {
        if (isFinishing() || isDestroyed() || binding == null || zineBinding == null) return;
        // 明信片要色板，色板要从底片上聚类——底片这时才第一次解
        if (!ensureSourceBitmap()) return;

        try {
            List<Integer> palette = zinePalette();
            ZinePostcardConfig cfg = collectZineConfig();

            Bitmap front = ZinePostcardRenderer.renderFront(sourceBitmap, palette, cfg, ZINE_PREVIEW_W);

            // 先换图，再回收旧图
            Bitmap oldFront = zineFrontBitmap;
            Bitmap oldBack = zineBackBitmap;
            zineFrontBitmap = front;
            zineBackBitmap = null;      // 背面作废，翻面时按新参数重渲
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

    /**
     * 需要时才渲背面。
     *
     * <p>背面和正面一样贵（同样要跑线稿 + 色块），而用户不一定翻面，
     * 所以不在 {@link #renderZinePreview()} 里一起渲。
     *
     * @return 背面是否可用
     */
    private boolean ensureZineBack() {
        if (zineBackBitmap != null && !zineBackBitmap.isRecycled()) return true;
        if (!ensureSourceBitmap()) return false;

        try {
            Bitmap back = ZinePostcardRenderer.renderBack(
                    sourceBitmap, zinePalette(), collectZineConfig(), ZINE_PREVIEW_W);
            zineBackBitmap = back;
            return back != null && !back.isRecycled();
        } catch (Throwable t) {
            Log.e(TAG, "明信片背面渲染失败", t);
            return false;
        }
    }

    /** 翻页式卡片翻转：正面 ↔ 背面 */
    private void flipZineCard() {
        if (isFinishing() || isDestroyed() || zineBinding == null) return;
        if (zineFrontBitmap == null || zineFrontBitmap.isRecycled()) {
            showToast("请先生成 Zine 明信片");
            return;
        }

        // 首次翻到背面才渲染背面：这一下要跑线稿，所以放在翻转动画之前算好
        if (!zineShowingBack && !ensureZineBack()) {
            showToast("明信片背面生成失败");
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
        if (!ensureSourceBitmap()) {
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
    //  胶片边框：照片集合 / 生成 / 导出
    // ====================================================================

    private FilmBorderConfig collectFilmConfig() {
        FilmBorderConfig cfg = new FilmBorderConfig();
        cfg.style = filmBinding.tvFilmStyle.getText().toString();
        cfg.width = filmBinding.tvFilmWidth.getText().toString();
        cfg.showCaption = filmBinding.switchFilmCaption.isChecked();
        cfg.frameNumber = filmBinding.switchFilmFrameNumber.isChecked();
        cfg.stock = filmBinding.etFilmStock.getText().toString();
        cfg.camera = filmBinding.etFilmCamera.getText().toString();
        cfg.lens = filmBinding.etFilmLens.getText().toString();
        cfg.exposure = filmBinding.etFilmExposure.getText().toString();
        cfg.date = filmBinding.etFilmDate.getText().toString();
        // 多张时参数行只认第一张的机型，其余交给装饰文案（见 FilmBorderConfig#resolveSpec）
        cfg.multiFrame = filmUris.size() > 1;
        return cfg;
    }

    /**
     * 从系统相册多选回来。
     *
     * <p>去重、限张数，然后交给 {@link #applyFilmPhotoSet}。
     * 包内可见而非 private：这条路径同样需要被测试直接驱动。
     */
    void onFilmPhotosPicked(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) return;

        List<Uri> kept = new ArrayList<>(new LinkedHashSet<>(uris));
        if (kept.size() > MAX_FILM_PHOTOS) {
            showToast("最多 " + MAX_FILM_PHOTOS + " 张，已保留前 " + MAX_FILM_PHOTOS + " 张");
            kept = new ArrayList<>(kept.subList(0, MAX_FILM_PHOTOS));
        }
        if (kept.isEmpty()) return;

        CrashLogger.breadcrumb("onFilmPhotosPicked " + kept.size() + " 张");
        applyFilmPhotoSet(kept);
    }

    /**
     * 让「主照片」跟着胶片照片集合走。
     *
     * <p>只有一张时，海报页与明信片页要用它，必须加载；
     * <b>多张时那两页会被锁住</b>（见 {@link #setPagerLocked}），主照片根本到不了，
     * 那就干脆放掉——它是一张 4K 位图加一张预览图，高像素手机上合起来上百 MB，
     * 正好把内存让给胶片那十几张。等照片减回 1 张，这里会自动补上。
     */
    private void syncMainPhotoWithFilmSet() {
        if (filmUris.size() > 1) {
            releaseSourcePhoto();
            return;
        }
        if (filmUris.size() != 1) return;

        Uri uri = filmUris.get(0);
        if (currentImageUri != null && currentImageUri.equals(uri)) return;

        currentImageUri = uri;
        loadSourceImage(uri, true);
    }

    /** 放掉主照片及其预览图（多张合成期间那两页到不了，留着纯占内存）。 */
    private void releaseSourcePhoto() {
        currentImageUri = null;
        recycleSourceBitmap();
        if (posterBinding != null) {
            posterBinding.imgPreview.setImageBitmap(null);
            posterBinding.tvEmptyHint.setVisibility(View.VISIBLE);
        }
        if (binding != null && binding.bgPhotoBackdrop != null) {
            binding.bgPhotoBackdrop.setImageBitmap(null);
        }
    }

    /** 放掉海报页的预览图。 */
    private void recyclePosterPreview() {
        if (posterBinding != null) {
            posterBinding.imgPreview.setImageBitmap(null);
        }
        recycleQuietly(posterPreviewBitmap);
        posterPreviewBitmap = null;
    }

    // ---- 仅供 MainActivityImportTest 断言内部状态 ----

    /** 预览图是否真的解出来并贴上了（用来确认导入不是空跑）。 */
    boolean hasPreviewForTest() {
        return posterBinding != null && posterBinding.imgPreview.getDrawable() != null;
    }

    /** 导出按钮的前置判断——多张合成时「主照片」被放掉，这里必须仍然为真。 */
    boolean hasPhotoForTest() {
        return hasPhoto();
    }

    /** 多张合成期间翻页是否被锁住。 */
    boolean isPagerLockedForTest() {
        return pagerLocked;
    }

    /** 当前胶片照片集合有多少张（用来验证第二批导入确实取代了第一批）。 */
    int filmPhotoCountForTest() {
        return filmUris.size();
    }

    /** 驱动「选择照片（可多选）」按钮的那段逻辑，用来验证忙时点击不会卡死。 */
    void onPickFilmPhotosTappedForTest() {
        if (filmBinding == null) return;
        filmBinding.btnPickFilmPhotos.performClick();
    }

    /**
     * 换一批胶片照片。
     *
     * <p><b>读尺寸、解码、渲染全部在后台线程做。</b>十几张照片的 IO 加解码
     * 放在主线程就是一次几秒的卡死——先是界面冻住，紧接着往往就是 ANR 或 OOM 退出。
     * 这里只做主线程该做的事：快照配置、更新提示、把结果贴回界面。
     *
     * <p>用「代次」（{@link #filmLoadGeneration}）作废旧结果：用户在读取途中又选了一批，
     * 先回来的那批直接丢掉并回收，不会覆盖新的。
     */
    private void applyFilmPhotoSet(List<Uri> uris) {
        if (filmBinding == null || isFinishing() || isDestroyed()) return;

        // 这一步之后到「胶片预览渲染完成」之间原本没有面包屑，
        // 真机崩在那里时日志只剩「onFilmPhotosPicked N 张」一行，什么也判断不了。
        CrashLogger.breadcrumb("applyFilmPhotoSet 开始 " + uris.size() + " 张");

        try {
            String key = buildFilmPhotoKey(uris);
            if (key.equals(filmPhotoKey) && !filmLoadPending && filmBitmaps.size() == filmUris.size()) {
                // 还是同一批照片：不重解码，也不清用户写的胶片型号
                updateFilmPhotoLabel();
                return;
            }
            filmPhotoKey = key;

            final int generation = ++filmLoadGeneration;
            final List<Uri> requested = new ArrayList<>(uris);
            // 配置必须在主线程快照：后台线程不能碰 View
            final FilmBorderConfig snapshot = collectFilmConfig();
            CrashLogger.breadcrumb("胶片配置快照完成");

            filmLoadPending = true;
            // 张数在选择结果里就已经知道了，不必等解码完才锁翻页
            setPagerLocked(requested.size() > 1);
            showFilmBusy(requested.size());
            CrashLogger.breadcrumb("翻页锁定=" + pagerLocked + "，提交后台解码");

            filmDecoder.execute(() -> loadFilmPhotos(generation, requested, snapshot));
        } catch (Throwable t) {
            // 界面上出任何问题都要退回来报一声，而不是把 App 带走
            Log.e(TAG, "启动胶片读取失败", t);
            CrashLogger.breadcrumb("applyFilmPhotoSet 抛出 " + t.getClass().getSimpleName()
                    + ": " + t.getMessage());
            filmLoadPending = false;
            showToast("读取胶片照片失败：" + t.getClass().getSimpleName());
        }
    }

    /**
     * 后台：只读尺寸 → 算出每格多大 → 按格解码。
     *
     * <p><b>每一步之前都查一次代次</b>。这一条是必须的，不是优化：
     * 第一次导入还在解十几张，用户又从底部「导入」选了一张，两次解码就会同时占内存——
     * 内存直接翻倍，正是「导入进程有冲突」那个画面。
     * 发现被取代就立刻停手、回收已经解出来的图，什么也不往界面上贴。
     */
    private void loadFilmPhotos(int generation, List<Uri> requested, FilmBorderConfig cfg) {
        // 后台线程不持有 Activity：解码只需要一个能给出缓存目录的 Context
        final Context decodeContext = getApplicationContext();
        CrashLogger.breadcrumb("胶片后台开始，共 " + requested.size() + " 张");

        List<Uri> uris = new ArrayList<>();
        List<int[]> sizes = new ArrayList<>();
        List<Bitmap> bitmaps = new ArrayList<>();
        String error = null;

        try {
            for (Uri uri : requested) {
                if (isFilmLoadCancelled(generation)) break;

                int[] size = new int[2];
                ImageProcessingHelper.readPhotoDimensions(decodeContext, uri, size);
                CrashLogger.breadcrumb("读尺寸 " + size[0] + "x" + size[1]);
                if (size[0] > 0 && size[1] > 0) {
                    uris.add(uri);
                    sizes.add(size);
                }
            }

            if (!isFilmLoadCancelled(generation) && !uris.isEmpty()) {
                cfg.multiFrame = uris.size() > 1;
                int[][] cellSizes = FilmBorderRenderer.cellSizesFor(
                        sizes, cfg, FILM_EXPORT_W, FILM_MIN_FRAME_PX);
                CrashLogger.breadcrumb("版面测算完成，开始解码 " + uris.size() + " 张");

                for (int i = 0; i < uris.size(); i++) {
                    if (isFilmLoadCancelled(generation)) break;

                    int[] target = decodeTarget(cellSizes, i, uris.size(), sizes.get(i));
                    CrashLogger.breadcrumb("解码第 " + (i + 1) + "/" + uris.size()
                            + " 张 → " + target[0] + "x" + target[1]);

                    Bitmap decoded = ImageProcessingHelper.loadSourceBitmap(
                            decodeContext, uris.get(i), target[0], target[1]);
                    decoded = ImageProcessingHelper.fitWithin(decoded, target[0], target[1]);
                    if (decoded != null && !decoded.isRecycled()) {
                        bitmaps.add(decoded);
                    }
                }
                CrashLogger.breadcrumb("解码完成，实得 " + bitmaps.size() + " 张");
            }
        } catch (Throwable t) {
            // 包括 OutOfMemoryError：解码十几张本来就可能吃紧，
            // 这里必须兜住并退回界面，而不是把 App 带走
            Log.e(TAG, "读取胶片照片失败", t);
            CrashLogger.breadcrumb("胶片解码抛出 " + t.getClass().getSimpleName() + ": " + t.getMessage());
            error = t.getClass().getSimpleName();
        }

        // 已经被新的一批取代：结果作废，回收掉，别再往界面上贴
        if (isFilmLoadCancelled(generation)) {
            for (Bitmap b : bitmaps) recycleQuietly(b);
            CrashLogger.breadcrumb("胶片读取被新的一批取代，已放弃这一批");
            return;
        }

        final List<Uri> outUris = uris;
        final List<int[]> outSizes = sizes;
        final List<Bitmap> outBitmaps = bitmaps;
        final String outError = error;
        runOnUiThread(() -> onFilmPhotosLoaded(generation, outUris, outSizes, outBitmaps, outError));
    }

    /** 这一批是不是已经被新的一批取代了。 */
    private boolean isFilmLoadCancelled(int generation) {
        return generation != filmLoadGeneration;
    }

    /**
     * 每一格的解码目标尺寸。
     *
     * <p>算不出格子时<b>不能</b>退回「按导出宽度解码」——十几张那样就是几百 MB。
     * 兜底改成把一批的像素预算均分给每一张。
     */
    private int[] decodeTarget(int[][] cellSizes, int index, int count, int[] sourceSize) {
        if (cellSizes != null && index < cellSizes.length) {
            return new int[]{
                    Math.max(64, Math.round(cellSizes[index][0] * FILM_DECODE_MARGIN)),
                    Math.max(64, Math.round(cellSizes[index][1] * FILM_DECODE_MARGIN))};
        }

        long perPhoto = Math.max(1L, FILM_DECODE_PIXEL_BUDGET / Math.max(1, count));
        float aspect = sourceSize[0] / (float) sourceSize[1];
        if (aspect <= 0f || Float.isNaN(aspect) || Float.isInfinite(aspect)) aspect = 1f;
        return new int[]{
                (int) Math.max(64, Math.round(Math.sqrt(perPhoto * aspect))),
                (int) Math.max(64, Math.round(Math.sqrt(perPhoto / aspect)))};
    }

    /** 后台读完，回主线程接管。 */
    private void onFilmPhotosLoaded(int generation, List<Uri> uris, List<int[]> sizes,
                                    List<Bitmap> bitmaps, String error) {
        if (generation != filmLoadGeneration || isFinishing() || isDestroyed() || filmBinding == null) {
            // 读取途中又换了一批 / 界面已经没了：这批结果作废
            for (Bitmap b : bitmaps) recycleQuietly(b);
            return;
        }

        CrashLogger.breadcrumb("回主线程贴界面：" + bitmaps.size() + " 张，error=" + error);

        try {
            applyLoadedFilmPhotos(uris, sizes, bitmaps, error);
        } catch (Throwable t) {
            Log.e(TAG, "贴回胶片照片失败", t);
            CrashLogger.breadcrumb("贴回界面抛出 " + t.getClass().getSimpleName()
                    + ": " + t.getMessage());
            for (Bitmap b : bitmaps) recycleQuietly(b);
            filmLoadPending = false;
            showToast("胶片照片处理失败：" + t.getClass().getSimpleName());
        }
    }

    private void applyLoadedFilmPhotos(List<Uri> uris, List<int[]> sizes,
                                       List<Bitmap> bitmaps, String error) {
        filmLoadPending = false;
        showFilmBusy(0);

        if (error != null) {
            for (Bitmap b : bitmaps) recycleQuietly(b);
            setPagerLocked(false);
            updateFilmPhotoLabel();
            showToast("读取照片失败（" + error + "），请少选几张再试");
            return;
        }

        recycleFilmBitmaps();
        filmUris.clear();
        filmUris.addAll(uris);
        filmSizes.clear();
        filmSizes.addAll(sizes);
        filmBitmaps.addAll(bitmaps);

        if (filmUris.isEmpty()) {
            setPagerLocked(false);
            updateFilmPhotoLabel();
            recycleFilmPreview();
            showToast("没能读取所选照片");
            return;
        }

        // 换了一批照片：用户写的胶片型号不再适用（机身/镜头等由 EXIF 重填）
        resetFilmCopyFields();
        CrashLogger.breadcrumb("胶片型号已清空，开始读片边 EXIF");
        applyFilmEdgeExif();
        CrashLogger.breadcrumb("片边 EXIF 完成");
        updateFilmPhotoLabel();
        setPagerLocked(filmUris.size() > 1);
        syncMainPhotoWithFilmSet();
        CrashLogger.breadcrumb("主照片同步完成，准备渲染胶片预览");

        recycleFilmPreview();
        if (isFilmMode()) {
            renderFilmPreview();
        }
        CrashLogger.breadcrumb("胶片预览渲染完成");
    }

    /** 后台读取期间的界面提示。 */
    private void showFilmBusy(int count) {
        if (filmBinding == null) return;
        if (count > 0) {
            filmBinding.tvFilmPhotoCount.setText("正在读取 " + count + " 张照片…");
            // 刻意不禁用按钮：禁用了点下去毫无反应，用户只会觉得「卡住了」。
            // 保持可点 + 点击时给一句提示，才知道是忙而不是死。
        }
    }

    private static String buildFilmPhotoKey(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Uri uri : uris) {
            sb.append(uri).append('\n');
        }
        return sb.toString();
    }

    /**
     * 按「每一格在成品里占多大」解码，而不是一律按原图。
     *
     * <p>十几张原图全量解码就是几百 MB；而解太小照片又会糊。
     * {@link ImageProcessingHelper#fitWithin} 再收一道，是因为 inSampleSize
     * 只能取 2 的幂，解码结果常常是目标的 1~2 倍——这个「多出来的部分」
     * 会乘以张数，正是最容易 OOM 的地方。
     *
     * <p>这条同步路径只在「滑回胶片页时补解一次」用到（见
     * {@link #ensureFilmPhotosDecoded}），张数已经定好、尺寸也已知，开销可控；
     * 用户主动选照片那一趟走的是后台线程。
     */
    private void decodeFilmPhotos() {
        int[][] cellSizes = FilmBorderRenderer.cellSizesFor(
                filmSizes, collectFilmConfig(), FILM_EXPORT_W, FILM_MIN_FRAME_PX);

        for (int i = 0; i < filmUris.size(); i++) {
            int[] target = decodeTarget(cellSizes, i, filmUris.size(), filmSizes.get(i));
            Bitmap decoded = ImageProcessingHelper.loadSourceBitmap(this, filmUris.get(i), target[0], target[1]);
            decoded = ImageProcessingHelper.fitWithin(decoded, target[0], target[1]);
            if (decoded != null && !decoded.isRecycled()) {
                filmBitmaps.add(decoded);
            }
        }
    }

    /**
     * 保证解码结果可用。
     *
     * <p>不变式：{@code filmBitmaps.size() == filmUris.size()} 才算有效。
     * 不在胶片页时这部分内存随时可以被回收（见 {@link #releaseFilmPhotos}），
     * 滑回来再按需解一次。
     */
    private boolean ensureFilmPhotosDecoded() {
        if (filmUris.isEmpty()) return false;
        if (filmBitmaps.size() == filmUris.size()) return true;
        // 后台正在读：这里不重复解码，等回调贴回来
        if (filmLoadPending) return false;
        decodeFilmPhotos();
        return !filmBitmaps.isEmpty();
    }

    /** 胶片页的解码结果只有胶片页用得到，离开这一页就可以放掉。 */
    private void releaseFilmPhotos() {
        if (isFilmMode()) return;
        recycleFilmBitmaps();
    }

    private void recycleFilmBitmaps() {
        for (Bitmap b : filmBitmaps) {
            recycleQuietly(b);
        }
        filmBitmaps.clear();
    }

    /**
     * 片边文字的 EXIF 预填。
     *
     * <p>单张走完整参数；<b>多张只留第一张的机型</b>——一组照片的拍摄参数
     * 往往不是一套（不同时间、不同镜头），硬拼成一行「机身 · 镜头 · 曝光 · 日期」
     * 反而处处是错的，剩下的交给本预设的装饰文案。
     */
    private void applyFilmEdgeExif() {
        if (filmBinding == null) return;

        if (filmUris.isEmpty()) {
            filmBinding.etFilmCamera.setText("");
            filmBinding.etFilmLens.setText("");
            filmBinding.etFilmExposure.setText("");
            filmBinding.etFilmDate.setText("");
            return;
        }

        String[] edge = ExifUtil.getFilmEdgeInfo(this, filmUris.get(0));
        filmBinding.etFilmCamera.setText(edge[0]);
        if (filmUris.size() > 1) {
            filmBinding.etFilmLens.setText("");
            filmBinding.etFilmExposure.setText("");
            filmBinding.etFilmDate.setText("");
        } else {
            filmBinding.etFilmLens.setText(edge[1]);
            filmBinding.etFilmExposure.setText(edge[2]);
            filmBinding.etFilmDate.setText(edge[3]);
        }
    }

    private void updateFilmPhotoLabel() {
        if (filmBinding == null) return;
        int count = filmUris.size();
        if (count == 0) {
            filmBinding.tvFilmPhotoCount.setText("未选择照片 · 最多 " + MAX_FILM_PHOTOS + " 张");
        } else if (count == 1) {
            filmBinding.tvFilmPhotoCount.setText("已选 1 张（底部「导入」也走这一张）");
        } else {
            // 行数由「整张稿子要是横版」自动决定，所以这里不再写「一行最多 N 格」
            filmBinding.tvFilmPhotoCount.setText("已选 " + count + " 张 · 自动排成横版\n"
                    + "多张合成期间左右翻页已锁定");
        }
    }

    /**
     * 边框风格 / 宽度 / 齿孔 / 帧号变化后调用：标记胶片边框待重渲染。
     *
     * <p>和明信片一样是懒的——不在边框页就只记脏位，等真的滑过去再渲染，
     * 免得用户只是改了个风格就在后台白白渲染一张大图。
     */
    private void markFilmDirty() {
        filmDirty = true;
        if (isFilmMode()) {
            renderFilmPreview();
        }
    }

    /**
     * 渲染胶片边框预览。
     *
     * <p>顺序要点同明信片：<b>先把新位图交给 ImageView，再回收旧位图</b>。
     * 反过来的话 View 仍持有旧位图，下一帧绘制就会抛
     * "Canvas: trying to use a recycled bitmap"。
     *
     * <p>预览不传 {@code minFramePx}：它要如实反映成品的构图，
     * 而「格子太小就把画布放大」是导出时才需要做的事。
     */
    private void renderFilmPreview() {
        if (isFinishing() || isDestroyed() || filmBinding == null) return;
        if (!ensureFilmPhotosDecoded()) {
            filmBinding.tvFilmEmptyHint.setVisibility(View.VISIBLE);
            return;
        }

        try {
            Bitmap rendered = FilmBorderRenderer.render(
                    filmBitmaps, collectFilmConfig(), FILM_PREVIEW_W, 0);
            if (rendered == null) {
                showToast("胶片边框生成失败");
                return;
            }
            CrashLogger.breadcrumb("胶片预览出图 " + rendered.getWidth() + "x" + rendered.getHeight());

            Bitmap old = filmPreviewBitmap;
            filmPreviewBitmap = rendered;
            filmBinding.imgFilmPreview.setImageBitmap(rendered);
            filmBinding.tvFilmEmptyHint.setVisibility(View.GONE);
            recycleQuietly(old);

            filmDirty = false;
        } catch (Throwable t) {
            Log.e(TAG, "renderFilmPreview 失败", t);
            showToast("胶片边框渲染失败：" + t.getMessage());
        }
    }

    /** 释放胶片边框预览位图；释放前先摘掉 ImageView 的引用。 */
    private void recycleFilmPreview() {
        if (filmBinding != null) {
            filmBinding.imgFilmPreview.setImageBitmap(null);
        }
        recycleQuietly(filmPreviewBitmap);
        filmPreviewBitmap = null;
        // 位图没了，下次回到边框页需要重新渲染
        filmDirty = true;
    }

    /**
     * 导出胶片边框。
     *
     * <p>渲染出的高清图直接交给 {@link ImageSaveHelper}——它会在保存结束后
     * 自己回收这张图，所以这里不占用 pendingExportBitmap（那是海报导出专用的）。
     *
     * <p>导出会传 {@code FILM_MIN_FRAME_PX}：多张排布时格子会被压小，
     * 这一步会把整张画布按比例放大，保证每一格都还有足够的像素。
     */
    private void exportFilmBorder() {
        if (!ensureFilmPhotosDecoded()) {
            showToast("请先导入照片");
            return;
        }
        try {
            Bitmap out = FilmBorderRenderer.render(
                    filmBitmaps, collectFilmConfig(), FILM_EXPORT_W, FILM_MIN_FRAME_PX);
            if (out == null) {
                showToast("胶片边框生成失败");
                return;
            }
            pendingFilmExport = true;
            showToast("正在保存胶片边框...");
            imageSaveHelper.saveBitmapToGallery(out);
        } catch (Throwable t) {
            pendingFilmExport = false;
            Log.e(TAG, "导出胶片边框失败", t);
            showToast("胶片边框生成失败：" + t.getMessage());
        }
    }

    // ====================================================================
    //  图片加载
    // ====================================================================

    /**
     * 导入一张照片（主照片）。
     *
     * <p><b>这一趟只解一张预览图，不解底片。</b>
     *
     * <p>原先这里会同时解两张：底片按 4000 解（12MP 手机照就是 4000×3000 ≈ 48MB），
     * 预览图又因为采样条件写得不巧，同样按原尺寸解出第二张 48MB ——
     * 光是导入一张照片就占掉 96MB，后面明信片、胶片再一动就是 OOM 退出。
     *
     * <p>现在底片改成<b>懒解码</b>（见 {@link #ensureSourceBitmap()}）：
     * 只有真的要出海报 / 明信片时才解，那时也是一次一张、用完就放。
     * 导入这一趟只剩预览图（最长边 1600，约 7.7MB）+ 读 EXIF，卡死与闪退的根子就在这。
     */
    private void loadSourceImage(Uri uri, boolean switchedPhoto) {
        try {
            CrashLogger.breadcrumb("loadSourceImage 开始 " + uri);
            int[] dimensions = new int[2];
            long totalPixels = ImageProcessingHelper.readPhotoDimensions(this, uri, dimensions);
            CrashLogger.breadcrumb("读尺寸 " + dimensions[0] + "x" + dimensions[1]);

            // 放掉上一张的底片；这一张的底片等真正要用时再解
            recycleSourceBitmap();

            Bitmap previewBitmap = ImageProcessingHelper.loadPreviewBitmap(this, uri, PREVIEW_MAX_PX, PREVIEW_MAX_PX);
            CrashLogger.breadcrumb("预览图解码 "
                    + (previewBitmap == null ? "失败"
                    : previewBitmap.getWidth() + "x" + previewBitmap.getHeight()));

            if (previewBitmap != null) {
                posterBinding.imgPreview.setImageBitmap(previewBitmap);

                // iOS 玻璃效果：将照片铺满全屏作为磨砂背景，并应用真实模糊（API 31+）。
                // 模糊之外还要提饱和度——玻璃本身几乎不着色，卡片上的颜色全部来自背景；
                // 背景一灰，玻璃就退化成一块白板（参考实现里 saturation 默认 140）。
                if (binding.bgPhotoBackdrop != null) {
                    binding.bgPhotoBackdrop.setImageBitmap(previewBitmap);
                    GlassEffectHelper.applyBackdropBlur(binding.bgPhotoBackdrop, 30f);
                    GlassEffectHelper.applyBackdropSaturation(
                            binding.bgPhotoBackdrop, 1.35f, 10f);
                }
                // 玻璃折的是背景，换了照片就得重拍快照
                refreshBackdropSnapshot();

                posterBinding.tvEmptyHint.setVisibility(View.GONE);

                // 换了照片：先清掉上一张遗留的创作文案。
                // 标题／副标题／序号是用户自己写的、不是 EXIF 派生，所以单独清；
                // 若这张照片在本地历史里有记录，随后的 checkAndLoadHistoryConfig
                // 会异步回填它自己上次保存的内容。
                if (switchedPhoto) {
                    resetZineCopyFields();
                    resetFilmCopyFields();
                }

                // 再填充照片自带的原始 EXIF（含明信片的 DATE / LOCATION）
                autoFillExif(uri);
                CrashLogger.breadcrumb("EXIF 预填完成");

                // 尝试从本地 Room 数据库读取此图片上一次的导出规格，如果存在则进行覆盖回填
                checkAndLoadHistoryConfig(uri);
                CrashLogger.breadcrumb("loadSourceImage 完成");

                // 明信片：只放掉旧预览并记脏位，不在这里渲染。
                // 渲染放到「页面完全停稳」时（见 renderSettledPage），
                // 这样导入这一趟不会有边缘检测 + 线稿重绘那几秒的开销。
                recycleZinePreview();

                // 胶片页的照片集合由 applyFilmPhotoSet() 单独驱动——
                // 它可能有多张，不能跟着主照片一起重置。

                if (ImageProcessingHelper.isHighResolution(totalPixels)) {
                    showToast("高清底片已就绪，已优化预览显示");
                }
            }
        } catch (Throwable e) {
            Log.e(TAG, "导入照片失败", e);
            CrashLogger.breadcrumb("导入照片抛出 " + e.getClass().getSimpleName() + ": " + e.getMessage());
            showToast("图片处理异常");
        }
    }

    /**
     * 底片的懒解码。
     *
     * <p>4K 底片一张就是 40~50MB，而它只有<b>预览海报 / 生成明信片 / 导出</b>时用得到。
     * 导入时不解、放到真正要用之前再解，出海报与明信片时也用同一份，
     * 用完由 {@link #recycleSourceBitmap()} 收掉。
     *
     * @return 底片是否可用
     */
    private boolean ensureSourceBitmap() {
        if (sourceBitmap != null && !sourceBitmap.isRecycled()) return true;
        if (currentImageUri == null) return false;

        try {
            sourceBitmap = ImageProcessingHelper.loadSourceBitmap(this, currentImageUri, SOURCE_MAX_PX, SOURCE_MAX_PX);
        } catch (Throwable t) {
            Log.e(TAG, "底片解码失败", t);
            sourceBitmap = null;
        }
        return sourceBitmap != null && !sourceBitmap.isRecycled();
    }

    /** 放掉底片（只放位图，不动 currentImageUri）。 */
    private void recycleSourceBitmap() {
        recycleQuietly(sourceBitmap);
        sourceBitmap = null;
    }

    /** 清空明信片里由用户撰写的文案字段（换照片时调用）。 */
    private void resetZineCopyFields() {
        zineBinding.etZineTitle.setText("");
        zineBinding.etZineSubtitle.setText("");
        zineBinding.etZineIndex.setText("01");
    }

    /**
     * 清空胶片边框里由用户撰写的胶片型号（换照片时调用）。
     *
     * <p>机身 / 镜头 / 曝光 / 日期都是 EXIF 派生，{@link #autoFillExif} 会无条件覆盖，
     * 所以这里不用管；胶片型号不是 EXIF 字段，只能在这里清。
     */
    private void resetFilmCopyFields() {
        filmBinding.etFilmStock.setText("");
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

        // 胶片边框的片边文字不走这里：它由 applyFilmEdgeExif() 单独负责，
        // 因为多张合成时只有「第一张的机型」该留下，规则与单张不同。
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
        if (!ensureSourceBitmap()) {
            showToast("请先导入照片");
            return;
        }

        try {
            updateInfoFromUI();
            String selectedMode = currentModeKey();
            List<Integer> palette = ColorExtractor.getPaletteByMode(sourceBitmap, selectedMode);
            String style = currentStyleKey();

            // 按预览尺度直接出图（0.25 → 960×540），而不是先渲 4K 再缩——
            // 预览只要 1/16 的像素，版面由 Canvas 矩阵等比缩下去，与成品完全一致
            Bitmap preview = PosterRenderer.render(
                    this, sourceBitmap, palette, exifInfoManager.getAll(), style, POSTER_PREVIEW_SCALE);

            if (preview != null) {
                Bitmap old = posterPreviewBitmap;
                posterPreviewBitmap = preview;
                posterBinding.imgPreview.setImageBitmap(preview);
                recycleQuietly(old);
                showToast("渲染完成");
            }
        } catch (Throwable e) {
            Log.e(TAG, "生成预览失败", e);
            showToast("生成预览失败: " + e.getMessage());
        }
    }

    private void processAndRender() {
        if (!ensureSourceBitmap()) return;
        generatePoster();
    }

    /**
     * 导出海报 - 已全面修复异步保存与内存回收引起的内存泄漏与闪退隐患
     */
    private void exportPoster() {
        if (!ensureSourceBitmap()) {
            showToast("请先导入照片");
            return;
        }

        updateInfoFromUI();

        String selectedMode = currentModeKey();
        List<Integer> palette = ColorExtractor.getPaletteByMode(sourceBitmap, selectedMode);
        String style = currentStyleKey();

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
        if (posterBinding == null) return;

        final boolean filmExport = pendingFilmExport;
        pendingFilmExport = false;

        // 历史的键：正常情况下是主照片；
        // 多张胶片合成时「主照片」已经被放掉（那两页被锁住了），
        // 就改用胶片的第一张——再次导入它即可恢复上次的胶片设置。
        Uri keyUri = currentImageUri != null
                ? currentImageUri
                : (filmUris.isEmpty() ? null : filmUris.get(0));
        if (keyUri == null) return;
        final String key = keyUri.toString();

        if (!filmExport) {
            insertHistory(buildPosterHistory(key, outputPathInfo, System.currentTimeMillis()));
            return;
        }

        // 胶片导出：胶片配置在主线程先快照好——下面的回调可能在后台线程，不能碰 View。
        final String filmJson = buildFilmJson();

        // 然后<b>读回该照片上一条记录</b>，把胶片配置并进去，而不是直接插一行新的。
        // 直接插会把海报那一半（渲染模式 / EXIF / 明信片文案）一起顶掉——
        // getLastConfigByUri 取的是最新一行，下次导入就恢复不出海报那套设置了。
        historyRepository.getLastConfig(key, previous -> {
            ExportHistory merged = new ExportHistory(
                    key,
                    previous == null ? "" : previous.optionsJson,
                    previous == null ? null : previous.sign,
                    outputPathInfo,
                    System.currentTimeMillis(),
                    previous == null ? null : previous.device,
                    previous == null ? null : previous.lens,
                    previous == null ? null : previous.shutter,
                    previous == null ? null : previous.aperture,
                    previous == null ? null : previous.iso,
                    previous == null ? null : previous.zineJson,
                    filmJson);
            insertHistory(merged);
        });
    }

    /** 海报 / 明信片导出的历史记录（这两个页面的状态都还在界面上，直接读）。 */
    private ExportHistory buildPosterHistory(String key, String outputPathInfo, long timestamp) {
        return new ExportHistory(
                key,
                pendingOptionsJson,
                pendingSignText,
                outputPathInfo,
                timestamp,
                posterBinding.etDevice.getText().toString(),
                posterBinding.etLens.getText().toString(),
                posterBinding.etShutter.getText().toString(),
                posterBinding.etAperture.getText().toString(),
                posterBinding.etIso.getText().toString(),
                buildZineJson(),
                buildFilmJson());
    }

    private void insertHistory(ExportHistory history) {
        historyRepository.insertHistory(history, id ->
                Log.d(TAG, "导出配置已记录到 Room，记录 ID: " + id));
    }

    /** 把当前胶片配置打包成 JSON，用于持久化到 Room。 */
    private String buildFilmJson() {
        if (filmBinding == null) return "";
        try {
            return collectFilmConfig().toJson();
        } catch (Throwable t) {
            Log.e(TAG, "打包胶片配置失败", t);
            return "";
        }
    }

    /**
     * 从历史记录恢复胶片配置。
     *
     * <p>恢复的是<b>设置</b>（风格 / 宽度 / 齿孔 / 帧号 / 片边文字），
     * 不是照片集合——一组照片没法从一行历史里还原出来。
     * 所以多张合成导出后，再次导入其中任意一张（含第一张）时会拿回这套设置，
     * 照片还得自己重选。
     */
    private void applyFilmJson(String json) {
        if (filmBinding == null || json == null || json.trim().isEmpty()) return;

        try {
            FilmBorderConfig cfg = FilmBorderConfig.fromJson(json, null);
            if (cfg == null) return;

            filmBinding.tvFilmStyle.setText(cfg.style);
            filmBinding.tvFilmWidth.setText(cfg.width);
            filmBinding.switchFilmCaption.setChecked(cfg.showCaption);
            filmBinding.switchFilmFrameNumber.setChecked(cfg.frameNumber);
            filmBinding.etFilmStock.setText(cfg.stock);
            filmBinding.etFilmCamera.setText(cfg.camera);
            filmBinding.etFilmLens.setText(cfg.lens);
            filmBinding.etFilmExposure.setText(cfg.exposure);
            filmBinding.etFilmDate.setText(cfg.date);

            // 上面的开关若值没变就不会触发回调，这里补一次，保证预览跟上
            markFilmDirty();
        } catch (Throwable t) {
            Log.e(TAG, "解析胶片配置失败", t);
        }
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
                                applyModeByKey(mode);
                            }
                            if (json.contains("\"style\":\"")) {
                                String style = json.split("\"style\":\"")[1].split("\"")[0];
                                applyStyleByKey(style);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    // 3.5 恢复上次保存明信片时填写的内容（标题/副标题/地点/日期/序号）
                    applyZineJson(history.zineJson);

                    // 3.6 恢复上次的胶片设置（风格/宽度/齿孔/帧号/片边文字）
                    applyFilmJson(history.filmJson);

                    Toast.makeText(MainActivity.this,
                            "已载入该照片上次的导出规格与 EXIF，点「预览渲染效果」查看",
                            Toast.LENGTH_SHORT).show();

                    // 4. 只记脏位，**不在这里渲染**。
                    //
                    //    这一步原来会直接跑一次完整的 4K 海报渲染：先解底片（40~50MB），
                    //    再跑 KMeans 取色，最后渲一张 3840×2160（33MB）只为缩成预览图。
                    //    导入这一趟本来就已经有解码和 EXIF 要做，再加上这一下，
                    //    在「同一张照片反复导入」（历史记录一定存在）时就是必崩的路径。
                    //    参数回填好就行，用户点按钮或下拉刷新时再渲。
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
                    // 和选择弹窗同一块面板：四角圆角、上沿高光、发丝边——整套 UI 一套语言
                    dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_popup_panel);
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
     * 内存吃紧时主动释放预览位图（明信片 + 胶片边框）。
     * 只释放当前没在看的那些——否则会把用户正在看的画面清空；
     * 释放后对应的 dirty 会被置位，下次滑回去会重新渲染。
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            if (!isZineMode()) {
                recycleZinePreview();
            }
            if (!isFilmMode()) {
                recycleFilmPreview();
            }
            // 胶片页的解码结果最占地方（十几张），不在这一页就直接放掉
            releaseFilmPhotos();
            // 底片是懒解码的，不在取色页就放掉——下次要用时自动补
            if (!isPage(PAGE_POSTER)) {
                recycleSourceBitmap();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (titleLongPressHelper != null) {
            titleLongPressHelper.cleanup();
        }
        // 只有用户真的退出（Back / 关掉任务）才算这一场正常结束；
        // 只是退到后台被杀，不该被当成异常
        if (isFinishing()) {
            CrashLogger.onCleanExit();
        }
        if (multiPhotoLockDialog != null && multiPhotoLockDialog.isShowing()) {
            multiPhotoLockDialog.dismiss();
        }
        multiPhotoLockDialog = null;
        if (binding != null) {
            binding.swipeWatch.setSwipeAttemptListener(null);
        }
        recycleZinePreview();
        recycleFilmPreview();
        recycleFilmBitmaps();
        recyclePosterPreview();
        recyclePendingExport();
        // 停掉解码线程：Activity 没了就没必要再把照片解出来
        filmDecoder.shutdownNow();
        if (sourceBitmap != null && !sourceBitmap.isRecycled()) {
            sourceBitmap.recycle();
        }
        sourceBitmap = null;
        // 置空三个页面的 binding，防止动画回调/异步回调再触碰已销毁的视图
        posterBinding = null;
        zineBinding = null;
        filmBinding = null;
        binding = null;
    }
}




















