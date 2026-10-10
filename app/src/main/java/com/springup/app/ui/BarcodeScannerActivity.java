package com.springup.app.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;
import com.springup.app.R;
import com.springup.app.databinding.ActivityBarcodeScannerBinding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class BarcodeScannerActivity extends AppCompatActivity {

    public static final String EXTRA_TARGET_BARCODE = "EXTRA_TARGET_BARCODE";
    public static final String EXTRA_SCANNED_VALUE = "EXTRA_SCANNED_VALUE";

    private ActivityBarcodeScannerBinding binding;
    private boolean isFlashOn = false;
    private String targetBarcode = null;
    private boolean isHandled = false;

    private final ActivityResultLauncher<String> requestCameraLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    startScanner();
                } else {
                    Toast.makeText(this, "Camera permission is required to scan barcodes.", Toast.LENGTH_LONG).show();
                    finish();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupScreenFlags();

        binding = ActivityBarcodeScannerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        targetBarcode = getIntent().getStringExtra(EXTRA_TARGET_BARCODE);
        if (targetBarcode != null && !targetBarcode.trim().isEmpty()) {
            binding.tvScanInstruction.setText("Scan matching barcode:\n" + targetBarcode);
        } else {
            binding.tvScanInstruction.setText("Point camera at barcode to register");
        }

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnToggleFlash.setOnClickListener(v -> toggleFlash());

        Collection<BarcodeFormat> formats = Arrays.asList(
                BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A,
                BarcodeFormat.UPC_E, BarcodeFormat.CODE_128, BarcodeFormat.CODE_39,
                BarcodeFormat.QR_CODE
        );
        binding.barcodeScannerView.getBarcodeView().setDecoderFactory(new DefaultDecoderFactory(formats));

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startScanner();
        } else {
            requestCameraLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void setupScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        );
    }

    private void startScanner() {
        binding.barcodeScannerView.decodeContinuous(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                if (isHandled || result.getText() == null) return;
                handleDetectedBarcode(result.getText().trim());
            }

            @Override
            public void possibleResultPoints(List<ResultPoint> resultPoints) {}
        });
        binding.barcodeScannerView.resume();
    }

    private void handleDetectedBarcode(String scannedValue) {
        if (scannedValue.isEmpty()) return;

        if (targetBarcode != null && !targetBarcode.trim().isEmpty()) {
            String sha = computeSha256(scannedValue);
            boolean matched = scannedValue.equalsIgnoreCase(targetBarcode)
                    || (sha != null && sha.equalsIgnoreCase(targetBarcode));

            if (matched) {
                isHandled = true;
                binding.barcodeScannerView.pause();
                vibrateSuccess();
                binding.tvScanStatus.setVisibility(View.VISIBLE);
                binding.tvScanStatus.setText("✓ Matched: " + scannedValue);
                binding.tvScanStatus.setTextColor(getColor(R.color.mission_success));

                Intent resultIntent = new Intent();
                resultIntent.putExtra(EXTRA_SCANNED_VALUE, scannedValue);
                setResult(RESULT_OK, resultIntent);
                binding.getRoot().postDelayed(this::finish, 500);
            } else {
                binding.tvScanStatus.setVisibility(View.VISIBLE);
                binding.tvScanStatus.setText("✗ Scanned: " + scannedValue + " (Does not match)");
                binding.tvScanStatus.setTextColor(getColor(R.color.brand_red));
            }
        } else {
            isHandled = true;
            binding.barcodeScannerView.pause();
            vibrateSuccess();
            binding.tvScanStatus.setVisibility(View.VISIBLE);
            binding.tvScanStatus.setText("✓ Captured: " + scannedValue);
            binding.tvScanStatus.setTextColor(getColor(R.color.mission_success));

            Intent resultIntent = new Intent();
            resultIntent.putExtra(EXTRA_SCANNED_VALUE, scannedValue);
            setResult(RESULT_OK, resultIntent);
            binding.getRoot().postDelayed(this::finish, 400);
        }
    }

    private void toggleFlash() {
        isFlashOn = !isFlashOn;
        if (isFlashOn) {
            binding.barcodeScannerView.setTorchOn();
        } else {
            binding.barcodeScannerView.setTorchOff();
        }
        binding.btnToggleFlash.setAlpha(isFlashOn ? 1.0f : 0.6f);
    }

    private void vibrateSuccess() {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(150);
            }
        }
    }

    private String computeSha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isHandled) binding.barcodeScannerView.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        binding.barcodeScannerView.pause();
    }

    @Override
    protected void onDestroy() {
        binding.barcodeScannerView.pause();
        super.onDestroy();
    }
}
