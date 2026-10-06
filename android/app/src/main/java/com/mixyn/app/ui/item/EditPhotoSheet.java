package com.mixyn.app.ui.item;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.slider.Slider;
import com.mixyn.app.R;

/**
 * "Edit photo": size, rotate left/right, mirror. Nothing is saved until Done; while the sheet is open
 * every change is only previewed on the item card behind it, and closing the sheet any other way cancels.
 */
public class EditPhotoSheet extends BottomSheetDialogFragment {

    /** Implemented by the screen that shows the photo. */
    public interface Listener {
        /** Show how the photo would look. rotation is clockwise: 0, 90, 180 or 270. */
        void onPhotoPreview(int scale, int rotation, boolean mirror);

        void onPhotoEditDone(int scale, int rotation, boolean mirror);

        void onPhotoEditCancelled();
    }

    private static final String ARG_SCALE = "scale";
    private static final int DEFAULT_SCALE = 100;
    private static final int STEP = 5;

    private int scale;
    private int rotation;
    private boolean mirror;
    private boolean done;
    private Slider slider;
    private TextView sizeValue;
    private TextView rotateValue;

    public static EditPhotoSheet newInstance(int currentScale) {
        Bundle args = new Bundle();
        args.putInt(ARG_SCALE, currentScale);
        EditPhotoSheet sheet = new EditPhotoSheet();
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_edit_photo, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        scale = requireArguments().getInt(ARG_SCALE, DEFAULT_SCALE);
        slider = view.findViewById(R.id.edit_size_slider);
        sizeValue = view.findViewById(R.id.edit_size_value);
        rotateValue = view.findViewById(R.id.edit_rotate_value);

        slider.setValue(scale);
        slider.addOnChangeListener((s, value, fromUser) -> {
            scale = Math.round(value);
            show();
        });
        view.findViewById(R.id.edit_size_minus).setOnClickListener(v -> slider.setValue(
                Math.max(slider.getValueFrom(), slider.getValue() - STEP)));
        view.findViewById(R.id.edit_size_plus).setOnClickListener(v -> slider.setValue(
                Math.min(slider.getValueTo(), slider.getValue() + STEP)));

        view.findViewById(R.id.edit_left).setOnClickListener(v -> turn(270));
        view.findViewById(R.id.edit_right).setOnClickListener(v -> turn(90));
        view.findViewById(R.id.edit_mirror).setOnClickListener(v -> {
            mirror = !mirror;
            show();
        });
        view.findViewById(R.id.edit_reset).setOnClickListener(v -> {
            rotation = 0;
            mirror = false;
            slider.setValue(DEFAULT_SCALE); // also triggers show()
            show();
        });
        view.findViewById(R.id.edit_done).setOnClickListener(v -> {
            done = true;
            listener().onPhotoEditDone(scale, rotation, mirror);
            dismiss();
        });
        show();
    }

    private void turn(int clockwiseDegrees) {
        rotation = (rotation + clockwiseDegrees) % 360;
        show();
    }

    /** Updates the numbers in the sheet and the live preview on the card. */
    private void show() {
        sizeValue.setText(getString(R.string.edit_photo_percent, scale));
        rotateValue.setText(getString(R.string.edit_photo_degrees, rotation));
        listener().onPhotoPreview(scale, rotation, mirror);
    }

    private Listener listener() {
        return (Listener) requireActivity();
    }

    @Override
    public void onStart() {
        super.onStart();
        // No dark overlay: the photo behind the sheet is the preview and must stay clearly visible.
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setDimAmount(0f);
        }
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (!done && getActivity() instanceof Listener l) l.onPhotoEditCancelled();
    }
}
