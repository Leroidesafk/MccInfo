package leroidesafk.mccinfo.client.relay;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;

// Only trusts the relay's own certificate (maybe the best security for the moment)
public final class PinnedTls {
	private PinnedTls() {
	}

	static SSLContext context(String pin) {
		byte[] expected = pin.getBytes(StandardCharsets.US_ASCII);
		X509TrustManager trust = new X509TrustManager() {
			@Override
			public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
				throw new CertificateException("client certificates are not accepted");
			}

			@Override
			public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
				if (chain == null || chain.length == 0) {
					throw new CertificateException("the relay sent no certificate");
				}
				// SHA-256 of the public key, compared to the pin (I'll ask to don't use this to make variants of the mod actually)
				String actual;
				try {
					actual = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(chain[0].getPublicKey().getEncoded()));
				} catch (GeneralSecurityException e) {
					throw new CertificateException(e);
				}
				if (!MessageDigest.isEqual(expected, actual.getBytes(StandardCharsets.US_ASCII))) {
					throw new CertificateException("unexpected relay certificate (" + actual + ")");
				}
			}

			@Override
			public X509Certificate[] getAcceptedIssuers() {
				return new X509Certificate[0];
			}
		};
		try {
			SSLContext context = SSLContext.getInstance("TLS");
			context.init(null, new TrustManager[]{trust}, null);
			return context;
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}
}
