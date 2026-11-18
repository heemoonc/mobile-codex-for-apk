package dev.mobilecodex.app;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;

/** Diagnostic fixture only. No network, external storage, audio, or user project access. */
public final class MediaProbeActivity extends Activity {
    private TextView status;
    private EditText draft;
    private ImageView preview;
    private final StringBuilder report = new StringBuilder();
    private int passed;
    private Context isolated;
    private android.content.SharedPreferences prefs;
    private File reportFile;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        File root = new File(getFilesDir(), "owned-media-probe"); root.mkdirs();
        isolated = new ContextWrapper(this) { @Override public File getFilesDir() { return root; } };
        reportFile = new File(root, "report.txt");
        prefs = getSharedPreferences("probe-state", 0);
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(1); layout.setPadding(24, 48, 24, 24);
        ScrollView scroll = new ScrollView(this); scroll.addView(layout); setContentView(scroll);
        TextView title = new TextView(this); title.setText("Android 실제 미디어 검증 · 단일 입력 최대 8MiB"); title.setTextSize(22); layout.addView(title);
        draft = new EditText(this); draft.setHint("초안 보존 검사 입력란"); draft.setText(prefs.getString("draft", "")); layout.addView(draft);
        status = new TextView(this); status.setTextSize(16); layout.addView(status);
        preview = new ImageView(this); preview.setAdjustViewBounds(true); preview.setMinimumHeight(100); preview.setContentDescription("제품 저장 코드로 저장한 3×2 PNG 미리보기"); layout.addView(preview);
        button(layout, "미디어 검사 실행", this::runProbe);
        button(layout, "저장 원본 재검증", this::verifyReopened);
        button(layout, "세로 회전", () -> setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
        button(layout, "가로 회전", () -> setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
        if (reportFile.isFile()) {
            try { status.setText(dev.mobilecodex.app.core.Utf8Files.read(reportFile.toPath())); showPreview(); }
            catch (Exception e) { status.setText("기록 읽기 실패: " + e.getClass().getSimpleName()); }
        } else status.setText("미실행 · 전용 앱 내부 데이터만 사용합니다.");
    }
    private void button(LinearLayout layout, String label, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setOnClickListener(v -> action.run()); layout.addView(b);
    }
    @Override public void onPause() {
        prefs.edit().putString("draft", draft.getText().toString()).commit(); super.onPause();
    }
    private void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
        passed++; report.append("PASS · ").append(name).append('\n');
    }
    private void expectIo(Checked action, String name) throws Exception {
        try { action.run(); } catch (IOException expected) { check(true, name); return; }
        throw new AssertionError("예외 없음: " + name);
    }
    private interface Checked { void run() throws Exception; }
    private int entries(String name) { File[] files = new File(isolated.getFilesDir(), name).listFiles(); return files == null ? 0 : files.length; }
    private String hash(byte[] bytes) throws Exception {
        StringBuilder out = new StringBuilder(); for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) out.append(String.format("%02x", b & 255)); return out.toString();
    }
    private void runProbe() {
        // Bound repeated runs: reuse this fixture's completed report rather than accumulating copies.
        if (prefs.contains("largeId")) { verifyReopened(); return; }
        if (prefs.getBoolean("attempted", false)) { status.setText("미완료 검사 기록이 있습니다. 파일 누적 방지를 위해 반복 실행하지 않습니다."); return; }
        report.setLength(0); passed = 0;
        try {
            if (!prefs.edit().putBoolean("attempted", true).commit()) throw new IOException("검사 시작 기록 저장 실패");
            ImageStore images = new ImageStore(isolated);
            AttachmentStore store = new AttachmentStore(isolated, images);
            byte[] png = android.util.Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAMAAAACCAYAAACddGYaAAAAEUlEQVR4nGMQMgn7D8MMyBwAdJkJo6/9UVsAAAAASUVORK5CYII=", 0);
            JSONObject picture = store.store(new ByteArrayInputStream(png), "native.png", "image/png");
            JSONObject image = picture.getJSONObject("image");
            check(image.getInt("width") == 3 && image.getInt("height") == 2, "네이티브 PNG 크기 3×2");
            check(Arrays.equals(png, Files.readAllBytes(new File(picture.getString("path")).toPath())), "첨부 이미지 원본 바이트 보존");
            try (InputStream in = images.open(image.getString("id"))) { check(Arrays.equals(png, in.readAllBytes()), "갤러리 이미지 원본 바이트 보존"); }
            Bitmap bitmap = BitmapFactory.decodeFile(images.file(image.getString("id")).getPath());
            check(bitmap != null && bitmap.getWidth() == 3 && bitmap.getHeight() == 2, "실제 미리보기 디코딩"); preview.setImageBitmap(bitmap);
            File imageFile = images.file(image.getString("id"));
            byte[] brokenImage = new byte[]{1, 2, 3};
            try {
                Files.write(imageFile.toPath(), brokenImage);
                expectIo(() -> decodedPreview(imageFile, hash(png)), "손상 미리보기 해시 불일치 차단");
                expectIo(() -> decodedPreview(imageFile, hash(brokenImage)), "디코딩 불가 미리보기 차단");
            } finally { Files.write(imageFile.toPath(), png); }
            check(store.inputs(new JSONArray().put(picture.getString("id"))).getJSONObject(0).getString("type").equals("localImage"), "이미지 프로토콜 입력");
            byte[] doc = "한글 원본\nanswer=42\n".getBytes(StandardCharsets.UTF_8);
            JSONObject first = store.store(new ByteArrayInputStream(doc), "../metadata.json", "text/plain");
            JSONObject second = store.store(new ByteArrayInputStream(new byte[]{7}), "../metadata.json", "text/plain");
            check(!first.getString("path").equals(second.getString("path")), "같은 이름 별도 원본");
            check(new File(first.getString("path")).getCanonicalFile().getParentFile().getName().equals(first.getString("id")), "제공자 이름 경로 탈출 방지");
            expectIo(() -> store.get("../outside"), "잘못된 첨부 ID 거부");
            expectIo(() -> images.file("../outside"), "잘못된 이미지 ID 거부");
            int before = entries("attachments");
            expectIo(() -> store.store(new InputStream() { public int read() throws IOException { throw new IOException("provider disconnected"); } }, "broken.pdf", null), "제공자 읽기 실패 전달");
            check(entries("attachments") == before, "읽기 실패 임시 첨부 정리");
            expectIo(() -> store.store(new ByteArrayInputStream(doc), "cancelled.txt", null, () -> true), "취소된 복사 차단");
            check(entries("attachments") == before, "취소된 임시 첨부 정리");
            AttachmentStore noSpace = new AttachmentStore(isolated, images) { @Override protected long availableBytes(File dir) { return 0; } };
            expectIo(() -> noSpace.store(new ByteArrayInputStream(doc), "no-space.txt", null), "여유 공간 0 모의 거부");
            check(entries("attachments") == before, "공간 거부 후 원본 목록 유지");
            AttachmentStore failing = new AttachmentStore(isolated, images) {
                @Override protected OutputStream writeFile(File file) throws IOException {
                    return new FilterOutputStream(new FileOutputStream(file)) {
                        @Override public void write(byte[] b, int off, int len) throws IOException { out.write(b, off, Math.min(3, len)); throw new IOException("fixture ENOSPC"); }
                    };
                }
            };
            expectIo(() -> failing.store(new ByteArrayInputStream(doc), "partial.txt", null), "출력 실패 모의 전달");
            check(entries("attachments") == before, "부분 쓰기 실패 정리");
            byte[] large = new byte[8 * 1024 * 1024]; Arrays.fill(large, (byte) 0x5a);
            JSONObject bulk = store.store(new ByteArrayInputStream(large), "bounded-8MiB.bin", "application/octet-stream");
            check(bulk.getLong("size") == 8388608L && bulk.getString("sha256").equals(hash(large)), "8MiB 복사 크기·SHA256");
            try (InputStream in = new AttachmentStore(isolated, new ImageStore(isolated)).open(bulk.getString("id"))) {
                check(Arrays.equals(large, in.readAllBytes()), "실패 후 다음 복사·재열기 성공");
            }
            check(Arrays.equals(doc, Files.readAllBytes(new File(first.getString("path")).toPath())), "모든 실패 후 기존 문서 원본 유지");
            prefs.edit().putString("largeId", bulk.getString("id")).putString("largeHash", hash(large))
                .putString("imageId", image.getString("id")).putString("imageHash", hash(png)).commit();
            report.append("결과: ").append(passed).append("개 통과 · 실제 디스크 고갈 검사는 아님\n");
            dev.mobilecodex.app.core.Utf8Files.write(reportFile.toPath(), report.toString()); status.setText(report.toString());
        } catch (Throwable error) {
            report.append("FAIL · ").append(error.getClass().getSimpleName()).append(": ").append(error.getMessage()); status.setText(report.toString());
        }
    }
    private Bitmap decodedPreview(File file, String expectedHash) throws Exception {
        if (!hash(Files.readAllBytes(file.toPath())).equals(expectedHash)) throw new IOException("미리보기 원본 해시 불일치");
        Bitmap bitmap = BitmapFactory.decodeFile(file.getPath());
        if (bitmap == null || bitmap.getWidth() != 3 || bitmap.getHeight() != 2) {
            if (bitmap != null) bitmap.recycle();
            throw new IOException("미리보기 디코딩 실패");
        }
        return bitmap;
    }
    private void showPreview() throws Exception {
        if (!prefs.contains("imageId")) throw new IOException("이미지 기록 없음");
        preview.setImageBitmap(decodedPreview(new ImageStore(isolated).file(prefs.getString("imageId", "")), prefs.getString("imageHash", "")));
    }
    private void verifyReopened() {
        try {
            String id = prefs.getString("largeId", "");
            byte[] bytes;
            try (InputStream in = new AttachmentStore(isolated, new ImageStore(isolated)).open(id)) { bytes = in.readAllBytes(); }
            if (bytes.length != 8388608 || !hash(bytes).equals(prefs.getString("largeHash", ""))) throw new IOException("원본 불일치");
            showPreview();
            status.setText(dev.mobilecodex.app.core.Utf8Files.read(reportFile.toPath()) + "재검증 PASS · 8MiB 원본·미리보기 재열기\n초안: " + draft.getText());
        } catch (Exception error) { status.setText("재검증 FAIL · " + error.getClass().getSimpleName()); }
    }
}
