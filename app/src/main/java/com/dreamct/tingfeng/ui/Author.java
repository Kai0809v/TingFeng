package com.dreamct.tingfeng.ui;

import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.dreamct.tingfeng.R;
import com.dreamct.tingfeng.utilities.LinkSpanUtil;

public class Author extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_author);
        //适配系统UI（如状态栏和导航栏）的边距
        //跟布局文件里的android:fitsSystemWindows="true"有差不多的效果，但是这个更灵活
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });


        //以下是为了使TextView可以点击并跳转
        TextView tvLink0 = findViewById(R.id.tv_link0);
        TextView tvLink1 = findViewById(R.id.tv_link1);
        //全部字段
        String wholeText0 = getString(R.string.link0);
        String wholeText1 = getString(R.string.link1);
        // 需要变成链接的关键词
        String keyword0 ="Github";
        String keyword1 ="QQ群";
        // 使用工具类设置可点击链接
        LinkSpanUtil.setClickableSpan(tvLink0, wholeText0, keyword0, "https://github.com/Kai0809v/TingFeng");
        LinkSpanUtil.setClickableSpan(tvLink1, wholeText1, keyword1, "https://example.com/join-us");

        /******
        SpannableString spannable0 = new SpannableString(wholeText0);
        SpannableString spannable1 = new SpannableString(wholeText1);
        int start0 = wholeText0.indexOf(keyword0);
        int end0 = start0 + keyword0.length();
        int start1 = wholeText1.indexOf(keyword1);
        int end1 = start1 + keyword1.length();

        // 创建点击事件
        ClickableSpan clickableSpan0 = new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                // 点击后跳转到浏览器
                String url = "https://github.com";
                Intent githubUrl = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(githubUrl);
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                super.updateDrawState(ds);
                ds.setUnderlineText(false);  // 去掉下划线（可选）
            }
        };

        ClickableSpan clickableSpan1 = new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                // 点击后跳转到另一个链接
                String url = "https://example.com/join-us";
                Intent joinUsUrl = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(joinUsUrl);
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                super.updateDrawState(ds);
                ds.setUnderlineText(false);  // 去掉下划线（可选）
            }
        };

        // 把指定区域设置成可点击
        if (start0 != -1) {//判断是否包含关键词，-1表示没有
            spannable0.setSpan(clickableSpan0, start0, end0, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            tvLink0.setText(spannable0);
            // 必须加这一句，否则点击不生效
            tvLink0.setMovementMethod(LinkMovementMethod.getInstance());
        } else {
            tvLink0.setText(wholeText0);
        }


        if (start1 != -1) {
            spannable1.setSpan(clickableSpan1, start1, end1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            tvLink1.setText(spannable1);
            tvLink1.setMovementMethod(LinkMovementMethod.getInstance());
        } else {
            tvLink1.setText(wholeText1);
        }

        ******/


    }


}