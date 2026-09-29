package io.github.neozmmv.enderlink.iroh;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import computer.iroh.EndpointId;
import computer.iroh.IrohException;

/**
 * The "iroh key" players share: an endpoint id written as 52 lowercase base32 chars.
 *
 * <p>iroh's usual 64-char hex form can't be typed as a server address, since Minecraft runs addresses
 * through {@link java.net.IDN}, which rejects hostname labels longer than 63 chars.
 */
public final class IrohKeys {
	private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz234567";
	private static final Pattern KEY = Pattern.compile("[a-z2-7]{52}");

	private IrohKeys() {
	}

	public static String encode(EndpointId id) {
		StringBuilder key = new StringBuilder(52);
		int buffer = 0;
		int bits = 0;
		for (byte b : id.toBytes()) {
			buffer = (buffer << 8) | (b & 0xFF);
			bits += 8;
			while (bits >= 5) {
				key.append(ALPHABET.charAt((buffer >> (bits - 5)) & 31));
				bits -= 5;
			}
		}
		if (bits > 0) {
			key.append(ALPHABET.charAt((buffer << (5 - bits)) & 31));
		}
		return key.toString();
	}

	public static Optional<EndpointId> parse(String text) {
		String key = text.toLowerCase(Locale.ROOT);
		if (!KEY.matcher(key).matches()) {
			return Optional.empty();
		}

		try {
			return Optional.of(EndpointId.Companion.fromString(key));
		} catch (IrohException e) {
			return Optional.empty();
		}
	}
}
