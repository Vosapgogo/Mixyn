package com.mixyn.app.ui.additem;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.mixyn.app.R;
import com.mixyn.app.api.ApiCallback;
import com.mixyn.app.api.ApiClient;
import com.mixyn.app.api.dto.ColorDto;
import com.mixyn.app.api.dto.ItemDto;
import com.mixyn.app.session.SessionStore;
import com.mixyn.app.ui.base.BaseActivity;
import com.mixyn.app.ui.components.Categories;
import com.mixyn.app.ui.item.ItemDetailActivity;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

/**
 * Add clothes: pick a photo from the gallery → upload → the backend removes the background and
 * tags it → "Background removed · 1/1 completed" → View item. Errors offer Try again.
 * A screen that sends the user here for one missing piece (the outfit canvas) gets it back as the
 * result as soon as it is added, without the "View item" step.
 */
public class AddItemActivity extends BaseActivity {

    /** Long side of the uploaded photo: plenty for a clean cutout, and far smaller than a camera original. */
    private static final int MAX_UPLOAD_SIDE = 1600;

    private final ExecutorService background = Executors.newSingleThreadExecutor();
    private ImageView photo;
    private TextView title;
    private TextView subtitle;
    private LinearProgressIndicator progress;
    private MaterialButton action;
    private TextView cancel;
    @Nullable
    private Uri pickedUri;

    public static final String RESULT_ITEM_ID = "itemId";
    private static final String EXTRA_FOR_RESULT = "forResult";

    /** How many photos the gallery lets the user tick; we accept only one and explain that if they pick more. */
    private static final int PICKER_MAX = 10;

    private final ActivityResultLauncher<PickVisualMediaRequest> picker =
            registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(PICKER_MAX), uris -> {
                if (uris.isEmpty()) {
                    // Closed the gallery without choosing: leave, unless a message with buttons is on screen.
                    if (action.getVisibility() != View.VISIBLE) finish();
                    return;
                }
                if (uris.size() > 1) {
                    showOnePhotoLimit();
                    return;
                }
                pickedUri = uris.get(0);
                upload(pickedUri);
            });

    public static void start(Context context) {
        context.startActivity(new Intent(context, AddItemActivity.class));
    }

    /**
     * For startActivityForResult: once a piece is added the screen closes with RESULT_OK and the new
     * item's id in RESULT_ITEM_ID. Leaving without adding anything is RESULT_CANCELED.
     */
    public static Intent forResult(Context context) {
        return new Intent(context, AddItemActivity.class).putExtra(EXTRA_FOR_RESULT, true);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_item);
        photo = findViewById(R.id.add_item_photo);
        title = findViewById(R.id.add_item_title);
        subtitle = findViewById(R.id.add_item_subtitle);
        progress = findViewById(R.id.add_item_progress);
        action = findViewById(R.id.add_item_action);
        cancel = findViewById(R.id.add_item_cancel);
        cancel.setOnClickListener(v -> finish());
        if (savedInstanceState == null) openPicker();
    }

    private void openPicker() {
        picker.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void upload(Uri uri) {
        showProcessing(uri);
        // Reading and shrinking the photo is slow work, so it stays off the main (UI) thread.
        background.execute(() -> {
            byte[] jpeg = readAsJpeg(uri);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (jpeg == null) {
                    showError(getString(R.string.error_photo_unreadable));
                    return;
                }
                MultipartBody.Part part = MultipartBody.Part.createFormData("photo", "photo.jpg",
                        RequestBody.create(MediaType.parse("image/jpeg"), jpeg));
                long userId = new SessionStore(this).getUserId();
                ApiClient.get(this).uploadItem(userId, part).enqueue(new ApiCallback<>(this) {
                    @Override
                    protected void onSuccess(@Nullable ItemDto item) {
                        if (item == null) return;
                        if (getIntent().getBooleanExtra(EXTRA_FOR_RESULT, false)) {
                            setResult(RESULT_OK, new Intent().putExtra(RESULT_ITEM_ID, item.id));
                            finish();
                        } else {
                            showDone(item);
                        }
                    }

                    @Override
                    protected void onError(@Nullable String code, @NonNull String message) {
                        if ("not_single_item".equals(code)) {
                            showNotSingleItem(message); // a person, several items, or not clothing
                        } else {
                            showError(message);
                        }
                    }
                });
            });
        });
    }

    /** Decodes any gallery photo (JPEG, PNG, HEIC…), scales it down and re-encodes it as JPEG. */
    @Nullable
    private byte[] readAsJpeg(Uri uri) {
        try {
            Bitmap bitmap;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(), uri),
                        (decoder, info, source) -> {
                            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                            int longSide = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                            if (longSide > MAX_UPLOAD_SIDE) {
                                float scale = (float) MAX_UPLOAD_SIDE / longSide;
                                decoder.setTargetSize(Math.round(info.getSize().getWidth() * scale),
                                        Math.round(info.getSize().getHeight() * scale));
                            }
                        });
            } else {
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    bitmap = BitmapFactory.decodeStream(in);
                }
            }
            if (bitmap == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    // ----- The three states of the screen -----

    private void showProcessing(Uri uri) {
        Glide.with(this).load(uri).into(photo);
        title.setText(R.string.add_item_processing);
        title.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        subtitle.setText(R.string.add_item_processing_hint);
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(true);
        action.setVisibility(View.INVISIBLE);
        cancel.setVisibility(View.INVISIBLE);
    }

    private void showDone(ItemDto item) {
        Glide.with(this).load(ApiClient.imageUrl(item.imageUrl)).into(photo);
        title.setText(R.string.add_item_done);
        title.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_check, 0, 0, 0);
        String detected = getString(R.string.add_item_detected,
                item.typeLabel != null ? item.typeLabel : getString(Categories.label(item.category)),
                colorNames(item));
        subtitle.setText(getString(R.string.add_item_done_count) + "\n" + detected);
        progress.setIndeterminate(false);
        progress.setProgressCompat(100, true);
        action.setText(R.string.add_item_view);
        action.setVisibility(View.VISIBLE);
        action.setOnClickListener(v -> {
            ItemDetailActivity.start(this, item);
            finish(); // Back from the item card lands in the closet
        });
        cancel.setVisibility(View.INVISIBLE);
    }

    /** The user ticked several photos: nothing is uploaded, and they are asked to choose just one. */
    private String colorNames(ItemDto item) {
        if (item.colors == null || item.colors.isEmpty()) return getString(R.string.color_unknown);
        List<String> names = new ArrayList<>();
        for (ColorDto color : item.colors) names.add(color.label);
        return String.join(getString(R.string.tags_joined_separator), names);
    }

    private void showOnePhotoLimit() {
        pickedUri = null;
        photo.setImageDrawable(null);
        title.setText(R.string.add_item_limit_title);
        title.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        subtitle.setText(R.string.add_item_limit_body);
        progress.setVisibility(View.INVISIBLE);
        action.setText(R.string.add_item_choose_one);
        action.setVisibility(View.VISIBLE);
        action.setOnClickListener(v -> openPicker());
        cancel.setVisibility(View.VISIBLE);
    }

    /** The AI saw a person, several items or no clothing: retrying the same photo won't help. */
    private void showNotSingleItem(String message) {
        pickedUri = null;
        title.setText(R.string.add_item_not_single_title);
        title.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        subtitle.setText(message);
        progress.setVisibility(View.INVISIBLE);
        action.setText(R.string.add_item_choose_another);
        action.setVisibility(View.VISIBLE);
        action.setOnClickListener(v -> openPicker());
        cancel.setVisibility(View.VISIBLE);
    }

    private void showError(String message) {
        title.setText(R.string.add_item_error_title);
        title.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        // The backend's reason, unless it only repeats the title.
        subtitle.setText(message.equals(getString(R.string.add_item_error_title)) ? "" : message);
        progress.setVisibility(View.INVISIBLE);
        action.setText(R.string.add_item_retry);
        action.setVisibility(View.VISIBLE);
        action.setOnClickListener(v -> {
            if (pickedUri != null) upload(pickedUri);
            else openPicker();
        });
        cancel.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        background.shutdown();
        super.onDestroy();
    }
}
