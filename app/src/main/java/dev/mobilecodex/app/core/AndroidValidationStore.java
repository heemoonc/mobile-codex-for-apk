package dev.mobilecodex.app.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** App-private, project-scoped evidence summaries; never stores terminal logs or credentials. */
public final class AndroidValidationStore {
    private static final Set<String> EXCLUDED = Set.of(".git", ".gradle", ".kotlin", ".idea", ".codex", ".agents", ".superpowers", ".runtime-cache", "node_modules", "build", "artifacts", "test-results", "playwright-report");
    private final Path directory;
    private final Map<String, JSONObject> cache = new HashMap<>();
    public AndroidValidationStore(Path directory) { this.directory = directory; }

    private static String hex(byte[] bytes) {
        StringBuilder result=new StringBuilder();
        for(byte b:bytes) result.append(String.format(Locale.ROOT,"%02x",b & 255));
        return result.toString();
    }
    private static String hash(byte[] bytes) {
        try { return hex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private String id(String key) {
        if (key == null || key.length() > 8192) throw new IllegalArgumentException("Invalid project key");
        return hash(key.getBytes(StandardCharsets.UTF_8));
    }
    private static JSONObject empty() throws JSONException {
        return new JSONObject().put("schema", 1).put("requestedApis", new JSONArray("[29,35,36]"))
            .put("records", new JSONArray()).put("storageWarning", false);
    }
    private JSONObject load(String key) throws JSONException {
        String id = id(key);
        if (cache.containsKey(id)) return cache.get(id);
        JSONObject value = empty();
        Path file = directory.resolve(id + ".json");
        try {
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(directory) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > 512 * 1024)
                    throw new IOException("Invalid history file");
                value = new JSONObject(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
                if (value.getInt("schema") != 1 || value.getJSONArray("records").length() > 30) throw new IOException("Invalid history schema");
                normalizeApis(value.getJSONArray("requestedApis"));
                JSONArray records = value.getJSONArray("records");
                for (int i=0; i<records.length(); i++) {
                    JSONObject r = records.getJSONObject(i);
                    if (Set.of("running", "stopping").contains(r.optString("status"))) r.put("status", "interrupted");
                }
                value.put("storageWarning", false);
            }
        } catch (Exception failure) {
            value = empty().put("storageWarning", true);
        }
        cache.put(id, value);
        return value;
    }
    public synchronized JSONObject read(String key) throws JSONException { return new JSONObject(load(key).toString()); }

    private static JSONArray normalizeApis(JSONArray values) throws JSONException {
        if (values == null || values.length() < 1 || values.length() > 20) throw new IllegalArgumentException("Choose 1–20 Android API levels");
        LinkedHashSet<Integer> unique = new LinkedHashSet<>();
        for (int i=0; i<values.length(); i++) {
            Object raw = values.get(i);
            if (!(raw instanceof Number n) || n.doubleValue() != n.intValue() || n.intValue()<16 || n.intValue()>100)
                throw new IllegalArgumentException("Android API must be an integer from 16 to 100");
            unique.add(((Number) raw).intValue());
        }
        return new JSONArray(unique);
    }
    public synchronized void setApis(String key, JSONArray apis) throws IOException, JSONException {
        JSONArray normalized = normalizeApis(apis);
        JSONObject next = read(key).put("requestedApis", normalized);
        save(key, next);
    }
    public synchronized void add(String key, JSONObject record) throws IOException, JSONException {
        if (record == null || record.toString().getBytes(StandardCharsets.UTF_8).length > 16384 || record.has("output"))
            throw new IllegalArgumentException("Evidence summary required; raw output is not stored");
        JSONObject next = read(key), copy = new JSONObject(record.toString());
        if (!copy.has("id")) copy.put("id", UUID.randomUUID().toString());
        JSONArray prior = next.getJSONArray("records"), records = new JSONArray();
        records.put(copy);
        for (int i=0; i<prior.length() && records.length()<30; i++)
            if (!copy.optString("id").equals(prior.getJSONObject(i).optString("id"))) records.put(prior.getJSONObject(i));
        next.put("records", records);
        save(key, next);
    }
    private void save(String key, JSONObject next) throws IOException, JSONException {
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Invalid evidence storage directory");
        Files.createDirectories(directory);
        Path file = directory.resolve(id(key) + ".json");
        if (Files.isSymbolicLink(file)) throw new IOException("Evidence link rejected");
        next.put("storageWarning", false);
        Path temporary = Files.createTempFile(directory, ".evidence-", ".tmp");
        try {
            try (FileOutputStream out = new FileOutputStream(temporary.toFile())) {
                out.write(next.toString().getBytes(StandardCharsets.UTF_8)); out.getFD().sync();
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            cache.put(id(key), next);
        } finally { Files.deleteIfExists(temporary); }
    }

    /** Changes to any non-generated project input invalidate prior execution evidence. */
    public static String sourceFingerprint(Path root) throws IOException {
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw new IOException("A direct project folder is required");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            fingerprint(root, root, digest, new long[]{0,0}, 0);
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new IOException(impossible); }
    }
    private static void fingerprint(Path root, Path directory, MessageDigest digest, long[] budget, int depth) throws IOException {
        if (depth>24) throw new IOException("Project source depth limit exceeded");
        List<Path> children = new ArrayList<>();
        try (DirectoryStream<Path> entries=Files.newDirectoryStream(directory)) {
            for (Path p: entries) {
                if (EXCLUDED.contains(p.getFileName().toString())) continue;
                if (++budget[0]>10000) throw new IOException("Project source file limit exceeded");
                children.add(p);
            }
        }
        children.sort(Comparator.comparing(p->p.getFileName().toString()));
        for (Path p:children) {
            if (Files.isSymbolicLink(p)) throw new IOException("Source links require separate validation: " + root.relativize(p));
            if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) { fingerprint(root,p,digest,budget,depth+1); continue; }
            if (!Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unsupported source file");
            long size=Files.size(p);
            if (size>16*1024*1024 || budget[1]+size>128*1024*1024) throw new IOException("Project source size limit exceeded");
            budget[1]+=size;
            digest.update(root.relativize(p).toString().replace(File.separatorChar,'/').getBytes(StandardCharsets.UTF_8));
            digest.update((byte)0);
            digest.update(java.nio.ByteBuffer.allocate(8).putLong(size).array());
            long consumed=0;
            try(InputStream in=Files.newInputStream(p,LinkOption.NOFOLLOW_LINKS)) {
                byte[] bytes=new byte[65536]; int count;
                while((count=in.read(bytes))!=-1){consumed+=count;if(consumed>size)throw new IOException("Source changed while hashing");digest.update(bytes,0,count);}
            }
            if(consumed!=size)throw new IOException("Source changed while hashing");
            digest.update((byte)0);
        }
    }
}
