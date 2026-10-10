package com.springup.app.ui;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.card.MaterialCardView;
import com.springup.app.R;
import com.springup.app.util.AudioStorageHelper;
import com.springup.app.util.AudioStorageHelper.SoundItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SoundPickerBottomSheet extends BottomSheetDialogFragment {

    public interface OnSoundSelectedListener {
        void onSoundSelected(@Nullable String uriStr, String displayName);
    }

    private OnSoundSelectedListener listener;
    private String currentSelectedUri;
    private SoundAdapter adapter;
    private List<SoundItem> soundItems = new ArrayList<>();
    private MediaPlayer previewPlayer;
    private String currentlyPlayingUri = "NONE";

    private final ActivityResultLauncher<String> audioPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    Context context = getContext();
                    if (context == null) return;
                    String permanentUri = AudioStorageHelper.copyAudioToInternalStorage(context, uri);
                    if (permanentUri != null) {
                        String name = AudioStorageHelper.getFileName(context, uri);
                        Toast.makeText(context, "Added to saved tones!", Toast.LENGTH_SHORT).show();
                        loadSavedSounds();
                        currentSelectedUri = permanentUri;
                        adapter.notifyDataSetChanged();
                        playPreview(permanentUri);
                    } else {
                        Toast.makeText(context, "Failed to read audio file", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    public static SoundPickerBottomSheet newInstance(@Nullable String initialUri, OnSoundSelectedListener listener) {
        SoundPickerBottomSheet sheet = new SoundPickerBottomSheet();
        sheet.currentSelectedUri = initialUri;
        sheet.listener = listener;
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_sound_picker, container, false);

        RecyclerView rv = view.findViewById(R.id.rvSavedSounds);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new SoundAdapter();
        rv.setAdapter(adapter);

        loadSavedSounds();

        view.findViewById(R.id.cardImportFromFiles).setOnClickListener(v -> {
            audioPickerLauncher.launch("audio/*");
        });

        view.findViewById(R.id.btnConfirmSound).setOnClickListener(v -> {
            stopPreview();
            if (listener != null) {
                String name = getDisplayNameForUri(currentSelectedUri);
                listener.onSoundSelected(currentSelectedUri, name);
            }
            dismiss();
        });

        return view;
    }

    private void loadSavedSounds() {
        Context context = getContext();
        if (context == null) return;
        soundItems = AudioStorageHelper.getSavedSounds(context);
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private String getDisplayNameForUri(String uriStr) {
        if (uriStr == null) return "Default Siren";
        for (SoundItem item : soundItems) {
            if (Objects.equals(item.getUri(), uriStr)) {
                return item.getName();
            }
        }
        return "Custom Tone";
    }

    private void playPreview(@Nullable String uriStr) {
        stopPreview();
        try {
            Context context = getContext();
            if (context == null) return;

            Uri soundUri;
            if (uriStr == null) {
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (soundUri == null) soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            } else {
                soundUri = Uri.parse(uriStr);
            }

            previewPlayer = new MediaPlayer();
            previewPlayer.setDataSource(context, soundUri);
            previewPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            previewPlayer.prepare();
            previewPlayer.start();
            currentlyPlayingUri = uriStr != null ? uriStr : "DEFAULT";

            if (adapter != null) adapter.notifyDataSetChanged();

            previewPlayer.setOnCompletionListener(mp -> {
                currentlyPlayingUri = "NONE";
                if (adapter != null) adapter.notifyDataSetChanged();
            });
        } catch (Exception e) {
            currentlyPlayingUri = "NONE";
        }
    }

    private void stopPreview() {
        if (previewPlayer != null) {
            try {
                if (previewPlayer.isPlaying()) previewPlayer.stop();
                previewPlayer.release();
            } catch (Exception ignored) {}
            previewPlayer = null;
        }
        currentlyPlayingUri = "NONE";
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    @Override
    public void onStop() {
        super.onStop();
        stopPreview();
    }

    private class SoundAdapter extends RecyclerView.Adapter<SoundAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_saved_sound, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SoundItem item = soundItems.get(position);
            holder.tvSoundName.setText(item.getName());

            if (item.isDefault()) {
                holder.tvSoundType.setText("Built-in Default");
            } else if (item.isCustom()) {
                holder.tvSoundType.setText("Imported Audio File");
            } else {
                holder.tvSoundType.setText("System Alarm Ringtone");
            }

            boolean isSelected = Objects.equals(item.getUri(), currentSelectedUri);
            holder.rbSelected.setChecked(isSelected);

            int strokeColor = isSelected
                    ? requireContext().getColor(R.color.brand_red)
                    : requireContext().getColor(R.color.divider_color);
            holder.card.setStrokeColor(strokeColor);
            holder.card.setStrokeWidth(isSelected ? 2 : 1);

            boolean isPlayingThis = (item.getUri() == null && "DEFAULT".equals(currentlyPlayingUri))
                    || (item.getUri() != null && item.getUri().equals(currentlyPlayingUri));

            holder.btnPlayPreview.setImageResource(isPlayingThis ? R.drawable.ic_pause : R.drawable.ic_play);

            View.OnClickListener selectAction = v -> {
                currentSelectedUri = item.getUri();
                notifyDataSetChanged();
                playPreview(item.getUri());
            };

            holder.card.setOnClickListener(selectAction);
            holder.rbSelected.setOnClickListener(selectAction);

            holder.btnPlayPreview.setOnClickListener(v -> {
                if (isPlayingThis) {
                    stopPreview();
                } else {
                    currentSelectedUri = item.getUri();
                    notifyDataSetChanged();
                    playPreview(item.getUri());
                }
            });
        }

        @Override
        public int getItemCount() {
            return soundItems.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            MaterialCardView card;
            RadioButton rbSelected;
            TextView tvSoundName;
            TextView tvSoundType;
            ImageButton btnPlayPreview;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                card = itemView.findViewById(R.id.cardSoundItem);
                rbSelected = itemView.findViewById(R.id.rbSoundSelected);
                tvSoundName = itemView.findViewById(R.id.tvSoundName);
                tvSoundType = itemView.findViewById(R.id.tvSoundType);
                btnPlayPreview = itemView.findViewById(R.id.btnPlayPreview);
            }
        }
    }
}
