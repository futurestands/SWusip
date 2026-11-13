import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/** Offline debug-pilot issuer. Run from the Android project root. No network. */
class IssuePilotGrant {
    public static void main(String[] args) throws Exception {
        if (!Files.isRegularFile(Path.of("app/build.gradle.kts")))
            throw new IllegalStateException("Run from the Android project directory containing app/build.gradle.kts");
        Path secret = Path.of(".local-pilot-authority/issuer.pk8");
        if (args.length == 1 && args[0].equals("init")) {
            Path publicPath = Path.of("app/src/debug/assets/pilot-authority.der");
            if (Files.exists(secret) || Files.exists(publicPath)) throw new IllegalStateException("Issuer or public authority already exists; refusing to overwrite");
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA"); gen.initialize(3072);
            KeyPair pair = gen.generateKeyPair(); Files.createDirectories(secret.getParent());
            Files.write(secret,pair.getPrivate().getEncoded(),StandardOpenOption.CREATE_NEW);
            Files.createDirectories(publicPath.getParent());
            Files.write(publicPath,pair.getPublic().getEncoded(),StandardOpenOption.CREATE_NEW);
            System.out.println("Created local pilot issuer. Keep .local-pilot-authority private and offline.");
            return;
        }
        if (args.length != 5 || !args[0].equals("issue"))
            throw new IllegalArgumentException("init OR issue <device challenge> <agent|administrator|revoked> <agent-id> <output.json>");
        if (!args[1].matches("[0-9a-f]{64}") || !args[2].matches("agent|administrator|revoked") || !args[3].matches("[A-Za-z0-9_.-]{1,64}"))
            throw new IllegalArgumentException("Invalid device, role or agent identifier");
        long expires = System.currentTimeMillis() + 30L*24*60*60*1000;
        byte[] payload = ("SWUSIP-GRANT-V1\n"+args[1]+"\n"+args[2]+"\n"+expires+"\n"+args[3]).getBytes(StandardCharsets.UTF_8);
        PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(secret)));
        Signature sig = Signature.getInstance("SHA256withRSA"); sig.initSign(key); sig.update(payload);
        String json = "{\"payload\":\""+Base64.getEncoder().encodeToString(payload)+"\",\"signature\":\""+Base64.getEncoder().encodeToString(sig.sign())+"\"}\n";
        Files.writeString(Path.of(args[4]),json,StandardOpenOption.CREATE_NEW);
        System.out.println("Created device-bound pilot grant, expires in 30 days. No PBX settings changed.");
    }
}
