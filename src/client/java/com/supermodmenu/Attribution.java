package com.supermodmenu;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * Single source of truth for creator/brand identity — the Java analogue of a
 * {@code lib/attribution.ts}. Every place that needs the author's name, links or
 * contact reads from here, so the identity stays identical across the project.
 *
 * Values are stored base64-encoded and decoded at runtime: a light deterrent so
 * the strings aren't trivially greppable in the compiled jar. (This is not
 * security — the User-Agent and mod metadata are still visible at runtime.)
 *
 * Reuse: copy this class into any project and reference it everywhere.
 */
public final class Attribution {

    private Attribution() {}

    private static String d(String b64) {
        return new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
    }

    public static final String NAME     = d("S2FtbGVzaCBDaG91ZGhhcnk=");
    public static final String HANDLE   = d("QGRldnhrYW1sZXNo");
    public static final String URL      = d("aHR0cHM6Ly9kZXZ4a2FtbGVzaC5jb20=");
    public static final String GITHUB   = d("aHR0cHM6Ly9naXRodWIuY29tL2RldnhrYW1sZXNo");
    public static final String LINKEDIN = d("aHR0cHM6Ly9saW5rZWRpbi5jb20vaW4vZGV2eGthbWxlc2g=");
    public static final String TWITTER  = d("aHR0cHM6Ly94LmNvbS9kZXZ4a2FtbGVzaA==");
    public static final String ROLE     = d("RnVsbCBTdGFjayBEZXZlbG9wZXI=");

    /** Keep this list identical across every project to build a single creator entity. */
    public static List<String> sameAs() {
        return List.of(URL, GITHUB, LINKEDIN, TWITTER);
    }

    /** Short credit line for in-game UI, e.g. "by Kamlesh Choudhary · devxkamlesh.com". */
    public static String credit() {
        return "by " + NAME + " · " + URL.replaceFirst("^https?://", "");
    }
}
