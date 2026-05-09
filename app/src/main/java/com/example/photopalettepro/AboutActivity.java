package com.example.photopalettepro;

import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        // 1. 获取标题控件并设置
        TextView title = findViewById(R.id.tvAboutTitle);
        if (title != null) {
            title.setText("PhotoPalettePro / 关于与 FAQ");
        }

        // 2. 获取内容控件
        TextView content = findViewById(R.id.tvAboutContent);
        if (content != null) {
            StringBuilder sb = new StringBuilder();

            // --- 设计初衷 ---
            sb.append("<b>【灵感起源】</b><br/>");
            sb.append("本应用的初衷是向 <b>PhotoColors</b> 致敬。我们非常喜爱它将摄影与色彩结合的极简理念，因此决定在 Android 平台上构建一个更具自动化与技术深度的色彩工具，让摄影师能更纯粹地记录光影背后的色谱。<br/><br/>");

            // --- 算法内幕 ---
            sb.append("<b>【算法内幕】</b><br/>");
            sb.append("• <b>Hybrid Pro 空间加权：</b> 我们在标准的颜色提取逻辑上加入了「空间位置加权」。算法会分析图像频域，优先提取视觉中心区域的高频色，同时自动剔除边缘噪点的干扰。<br/>");
            sb.append("• <b>15 次迭代聚类：</b> 内部运行的 KMeans 逻辑经过多次次数学迭代，能从数像素矩阵中精准收敛出 5-15 个最具代表性的核心色值。<br/>");
            sb.append("• <b>HSL 智能排序流：</b> 所有的色块排列并非随机，而是通过复杂的 HSL 转换逻辑，按照色相升序与亮度降序重组，构建视觉上的绝对平衡。<br/><br/>");

            // --- 设计巧思 ---
            sb.append("<b>【设计巧思】</b><br/>");
            sb.append("• <b>全自动排版系统：</b> 这是一个真正的「免干预」系统。应用会自动识别照片的长宽比，动态计算海报各元素的坐标。无论横竖构图，文字信息与色块都会自动寻找黄金分割位进行重排。<br/>");
            sb.append("• <b>自适应环境色：</b> 界面底色是基于调色板中最亮色生成的自适应灰阶，旨在模拟专业画廊的布光环境，减少长时间观看的疲劳。<br/>");
            sb.append("• <b>极简交互彩蛋：</b> 你正通过长按这种隐藏方式阅读这段文字。我们将复杂的设置深藏，只在主界面留下一张白纸、一份纯粹。<br/><br/>");

            // --- 联系方式 ---
            sb.append("<b>【联系与支持】</b><br/>");
            sb.append("• <b>GitHub:</b> <a href='https://github.com/moudou517'>@moudou517</a> (欢迎 Star/交流)<br/>");
            sb.append("• <b>Email:</b> 443817851@qq.com<br/><br/>");

            sb.append("<b>【开发者信息】</b><br/>");
            sb.append("Build by <b>moudou517</b> with ❤️<br/>");
            sb.append("v1.0.0-Stable (2026.05)");

            // 渲染 HTML
            content.setText(Html.fromHtml(sb.toString(), Html.FROM_HTML_MODE_COMPACT));
            // 激活 GitHub 链接点击
            content.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        }
        
        // 3. 为返回按钮添加点击事件
        Button btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                // 返回到 MainActivity
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                // 添加退出动画效果
                overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
            });
        }
    }
}