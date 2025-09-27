package com.dreamct.tingfeng.utilities;

import android.content.Intent;
import android.net.Uri;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

public class LinkSpanUtil {

    /**
     * 为TextView中的指定关键词添加可点击链接
     * @param textView 目标TextView
     * @param fullText 完整文本
     * @param keyword 要添加链接的关键词
     * @param url 点击后要跳转的URL
     * @param removeUnderline 是否移除下划线
     */
    public static void setClickableSpan(TextView textView, String fullText, String keyword, String url, boolean removeUnderline) {
        SpannableString spannableString = new SpannableString(fullText);
        int start = fullText.indexOf(keyword);

        if (start != -1) {
            int end = start + keyword.length();

            ClickableSpan clickableSpan = new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    widget.getContext().startActivity(intent);
                }

                @Override
                public void updateDrawState(@NonNull TextPaint ds) {
                    super.updateDrawState(ds);
                    if (removeUnderline) {
                        ds.setUnderlineText(false);
                    }
                }
            };

            spannableString.setSpan(clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            textView.setText(spannableString);
            textView.setMovementMethod(LinkMovementMethod.getInstance());
        } else {
            textView.setText(fullText);
        }
    }

    /**
     * 为TextView中的指定关键词添加可点击链接（默认移除下划线）
     */
    public static void setClickableSpan(TextView textView, String fullText, String keyword, String url) {
        setClickableSpan(textView, fullText, keyword, url, true);
    }
}
