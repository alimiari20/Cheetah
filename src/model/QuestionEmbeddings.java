package model;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.util.Utils;
import ai.onnxruntime.*;
import java.io.IOException;
import java.nio.LongBuffer;
import java.nio.file.*;
import java.util.*;

/** Local multilingual MiniLM inference using the model's tokenizer and masked mean pooling. */
final class QuestionEmbeddings {
    private final OrtEnvironment environment;
    private final OrtSession session;
    private final HuggingFaceTokenizer tokenizer;

    QuestionEmbeddings() throws Exception {
        Path assets = assets();
        Path model = assets.resolve("model.onnx");
        Path vocabulary = assets.resolve("tokenizer.json");
        if (!Files.isRegularFile(model) || !Files.isRegularFile(vocabulary)) {
            throw new IOException("Question model files are missing.");
        }
        environment = OrtEnvironment.getEnvironment();
        tokenizer = loadTokenizer(vocabulary);
        try (var options = new OrtSession.SessionOptions()) {
            options.setIntraOpNumThreads(2);
            session = environment.createSession(model.toString(),options);
        } catch (Exception | LinkageError ex) {
            tokenizer.close();
            throw ex;
        }
    }

    private static synchronized HuggingFaceTokenizer loadTokenizer(Path vocabulary) throws IOException {
        String os=System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String arch=System.getProperty("os.arch");
        String platformArch=switch(arch) {
            case "amd64","x86_64" -> "x86_64";
            case "aarch64","arm64" -> "aarch64";
            default -> throw new IOException("Unsupported question tokenizer architecture: "+arch);
        };
        String platform=(os.startsWith("windows")?"win":os.startsWith("mac")?"osx":os)+"-"+platformArch;
        String library=System.mapLibraryName("tokenizers");
        List<String> files=os.startsWith("windows")
                ? List.of("libwinpthread-1.dll","libgcc_s_seh-1.dll","libstdc++-6.dll",library)
                : List.of(library);
        Path cache=Utils.getCacheDir().resolve("cheetah-question-tokenizer-0.33.0").resolve(platform);
        Files.createDirectories(cache);
        // Resolve JNI directly from the tokenizer JAR when it is reached through the app manifest.
        for(String file:files) {
            Path destination=cache.resolve(file);
            if(Files.isRegularFile(destination)) continue;
            try(var nativeFile=HuggingFaceTokenizer.class.getResourceAsStream("/native/lib/"+platform+"/cpu/"+file)) {
                if(nativeFile==null) throw new IOException("Bundled question tokenizer is unavailable for "+platform);
                Path temporary=Files.createTempFile(cache,"tokenizer-",".tmp");
                try {
                    Files.copy(nativeFile,temporary,StandardCopyOption.REPLACE_EXISTING);
                    try { Files.move(temporary,destination); }
                    catch(FileAlreadyExistsException ignored) { /* Another process extracted this file. */ }
                } finally {
                    Files.deleteIfExists(temporary);
                }
            }
        }
        String key="RUST_LIBRARY_PATH";
        String previous=System.setProperty(key,cache.resolve(library).toAbsolutePath().toString());
        try {
            return HuggingFaceTokenizer.newInstance(vocabulary,Map.of("truncation","true","maxLength","128"));
        } finally {
            if(previous==null) System.clearProperty(key);
            else System.setProperty(key,previous);
        }
    }

    private static Path assets() throws Exception {
        String override = System.getProperty("cheetah.questionModel");
        if (override != null) return Path.of(override).toAbsolutePath();
        Path source = Path.of(QuestionEmbeddings.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (Files.isDirectory(source)) {
            Path adjacent=source.resolve("model");
            return Files.isDirectory(adjacent)?adjacent:Path.of("target","question-ai-assets","model").toAbsolutePath();
        }
        return source.getParent().resolve("model");
    }

    float[] embed(String text) throws OrtException {
        var encoded = tokenizer.encode(text);
        long[] ids = encoded.getIds(), mask = encoded.getAttentionMask();
        long[] shape = {1, ids.length};
        if (ids.length < 1 || ids.length > 128) throw new IllegalArgumentException("Invalid tokenizer length");
        try (var input = tensor(ids,shape); var attention = tensor(mask,shape);
             var types = tensor(encoded.getTypeIds(),shape)) {
            Map<String,OnnxTensor> inputs = new HashMap<>();
            inputs.put("input_ids",input);
            inputs.put("attention_mask",attention);
            if (session.getInputNames().contains("token_type_ids")) inputs.put("token_type_ids",types);
            try (var result = session.run(inputs)) {
                float[][] tokens = ((float[][][])result.get("last_hidden_state").orElseThrow().getValue())[0];
                float[] pooled = new float[tokens[0].length];
                for(int i=0;i<tokens.length;i++) if(mask[i]!=0)
                    for(int j=0;j<pooled.length;j++) pooled[j]+=tokens[i][j];
                double sum=0;
                for(float value:pooled) sum+=value*value;
                double norm=Math.sqrt(sum);
                if (!(norm>0) || !Double.isFinite(norm)) throw new IllegalStateException("Invalid question embedding");
                for(int i=0;i<pooled.length;i++) pooled[i]/=norm;
                return pooled;
            }
        }
    }

    private OnnxTensor tensor(long[] data,long[] shape) throws OrtException {
        return OnnxTensor.createTensor(environment,LongBuffer.wrap(data),shape);
    }
}
