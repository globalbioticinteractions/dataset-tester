package org.globalbioticinteractions;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eol.globi.util.ResourceUtil;
import org.joda.time.format.ISODateTimeFormat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class PullThroughCache implements ResourceCache {
    private static final String SHA_256 = "SHA-256";
    private final static Log LOG = LogFactory.getLog(PullThroughCache.class);
    private String namespace;
    private String cachePath;

    public PullThroughCache(String namespace, String cachePath) {
        this.namespace = namespace;
        this.cachePath = cachePath;
    }

    private static void appendCacheLog(String namespace, URI resourceURI, File cacheDir, URI localResourceCacheURI) throws IOException {
        Date accessedAt = new Date();
        String sha256 = new File(localResourceCacheURI).getName();
        URIMeta meta = new URIMeta(namespace, resourceURI, localResourceCacheURI, sha256, accessedAt);
        appendAccessLog(meta, cacheDir);
    }

    private static void appendAccessLog(URIMeta meta, File cacheDirFile) throws IOException {
        List<String> accessLogEntry = compileLogEntries(meta);
        File accessLog = new File(cacheDirFile, "access.tsv");
        String prefix = accessLog.exists() ? "\n" : "";
        String accessLogLine = StringUtils.join(accessLogEntry, '\t');
        FileUtils.writeStringToFile(accessLog, prefix + accessLogLine, true);
    }

    static List<String> compileLogEntries(URIMeta meta) {
        return Arrays.asList(meta.getNamespace()
                , meta.getSourceURI().toString()
                , toContentHash(meta.getSha256())
                , ISODateTimeFormat.dateTimeNoMillis().withZoneUTC().print(meta.getAccessedAt().getTime()));
    }

    private static String toContentHash(String sha256) {
        return sha256 + ":" + SHA_256;
    }

    static File cache(URI sourceURI, File cacheDir) throws IOException {
        InputStream sourceStream = ResourceUtil.asInputStream(sourceURI, null);

        File destinationFile = File.createTempFile("archive", "tmp", cacheDir);
        destinationFile.deleteOnExit();

        String msg = "caching [" + sourceURI + "]";
        LOG.info(msg + " started...");
        try {
            MessageDigest md = MessageDigest.getInstance(SHA_256);
            DigestInputStream digestInputStream = new DigestInputStream(sourceStream, md);
            FileUtils.copyInputStreamToFile(digestInputStream, destinationFile);
            IOUtils.closeQuietly(digestInputStream);
            String sha256 = String.format("%064x", new java.math.BigInteger(1, md.digest()));
            File destFile = new File(cacheDir, sha256);
            FileUtils.deleteQuietly(destFile);
            FileUtils.moveFile(destinationFile, destFile);
            LOG.info(msg + " cached at [" + destFile.toURI().toString() + "]...");
            LOG.info(msg + " complete.");
            return destFile;
        } catch (NoSuchAlgorithmException e) {
            LOG.error("failed to access hash/digest algorithm", e);
            throw new IOException("failed to cache dataset [" + sourceURI.toString() + "]");
        }
    }

    @Override
    public URI asURI(URI resourceURI) throws IOException {
        File cacheDir = DatasetFinderCaching.getCacheDirForNamespace(cachePath, namespace);
        File resourceCached = cache(resourceURI, cacheDir);
        appendCacheLog(namespace, resourceURI, cacheDir, resourceCached.toURI());
        return resourceCached.toURI();
    }

    @Override
    public URIMeta asMeta(URI resourceURI) {
        return null;
    }

    @Override
    public InputStream asInputStream(URI resourceURI) throws IOException {
        URI resourceURI1 = asURI(resourceURI);
        return resourceURI1 == null ? null : ResourceUtil.asInputStream(resourceURI1, null);
    }
}

