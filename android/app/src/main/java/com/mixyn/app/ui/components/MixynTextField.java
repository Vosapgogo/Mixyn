package com.mixyn.app.ui.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.mixyn.app.R;

/**
 * The one input field used on every form: label, rounded box, optional show/hide for passwords,
 * and an error line. The box is an AutoCompleteTextView so the same component can suggest values (City).
 */
public class MixynTextField extends LinearLayout {

    private static final int TYPE_TEXT = 0;
    private static final int TYPE_NAME = 1;
    private static final int TYPE_EMAIL = 2;
    private static final int TYPE_PASSWORD = 3;

    private final TextView label;
    private final AutoCompleteTextView input;
    private final ImageButton toggle;
    private final TextView error;
    private boolean passwordVisible;

    public MixynTextField(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.view_text_field, this, true);
        label = findViewById(R.id.field_label);
        input = findViewById(R.id.field_input);
        toggle = findViewById(R.id.field_toggle);
        error = findViewById(R.id.field_error);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.MixynTextField);
        label.setText(a.getString(R.styleable.MixynTextField_label));
        input.setHint(a.getString(R.styleable.MixynTextField_hint));
        int type = a.getInt(R.styleable.MixynTextField_fieldType, TYPE_TEXT);
        a.recycle();

        // Each field gets its own id so Android can restore typed text after rotation.
        input.setId(View.generateViewId());
        applyType(type);

        // Once the user starts fixing the field, the red error goes away.
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (error.getVisibility() == VISIBLE) setError(null);
            }
        });
    }

    private void applyType(int type) {
        // Must come first: setSingleLine() replaces the transformation method and would unmask passwords.
        input.setSingleLine(true);
        // The keyboard shows "Next" and moves to the following field; the last one gets "Done" automatically.
        input.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        switch (type) {
            case TYPE_NAME -> input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME
                    | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            case TYPE_EMAIL -> input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            case TYPE_PASSWORD -> {
                input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                input.setTransformationMethod(PasswordTransformationMethod.getInstance());
                input.setPaddingRelative(input.getPaddingStart(), 0,
                        getResources().getDimensionPixelSize(R.dimen.icon_button_size), 0);
                toggle.setVisibility(VISIBLE);
                toggle.setOnClickListener(v -> togglePassword());
            }
            default -> input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        }
        input.setTypeface(getResources().getFont(R.font.inter));
    }

    private void togglePassword() {
        passwordVisible = !passwordVisible;
        input.setTransformationMethod(passwordVisible
                ? HideReturnsTransformationMethod.getInstance()
                : PasswordTransformationMethod.getInstance());
        input.setSelection(input.length());
        toggle.setImageResource(passwordVisible ? R.drawable.ic_eye_off : R.drawable.ic_eye);
        toggle.setContentDescription(getContext().getString(passwordVisible ? R.string.hide_password : R.string.show_password));
    }

    public AutoCompleteTextView getInput() {
        return input;
    }

    /** The typed value, trimmed (passwords are returned exactly as typed). */
    public String getValue() {
        String value = input.getText().toString();
        return toggle.getVisibility() == VISIBLE ? value : value.trim();
    }

    public void setValue(String value) {
        input.setText(value);
    }

    /** Shows an error under the field; pass null to clear it. */
    public void setError(@Nullable CharSequence message) {
        error.setText(message);
        error.setVisibility(message == null ? GONE : VISIBLE);
        input.setBackgroundResource(message == null ? R.drawable.bg_field : R.drawable.bg_field_error);
    }
}
