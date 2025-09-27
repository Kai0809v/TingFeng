package com.dreamct.tingfeng.utilities;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;

import com.google.android.material.textfield.TextInputEditText;

import java.util.Objects;


/**输入验证器类，用于验证输入的有效性\n
 * 是否都不为空？
 * 是否太长了？*/
public class InputValidator {
    /**设置文本观察者*/
    public static void setupTextWatcher(TextInputEditText[] inputs, Button button) {
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validateInputs(inputs, button);
            }
            /**禁止输入空格*/
            @Override
            public void afterTextChanged(Editable s) {
                if (s.toString().contains(" ")) {
                    s.replace(0, s.length(), s.toString().replace(" ", ""));
                }
            }
        };

        for (TextInputEditText input : inputs) {
            input.addTextChangedListener(watcher);
        }
    }

    /** 这里需要传入两个参数，一个数组，和一个按钮；
     * 这里循环遍历数组中的每一个元素，如果有一个为空，就将按钮设置为不可用，跳出循环
     * 如果太长了，比如这里我设置16个字节，也同上**/
    private static void validateInputs(TextInputEditText[] inputs, Button button) {
        boolean isValid = true;
        for (TextInputEditText input : inputs) {
            if (Objects.requireNonNull(input.getText()).toString().trim().isEmpty()) {
                isValid = false;
                break;
            }if (Objects.requireNonNull(input.getText()).toString().trim().length()>16) {
                isValid = false;
                break;
            }
        }
        button.setEnabled(isValid);
    }
}