package org.globalbioticinteractions;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.FileFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.lang.StringUtils;
import org.eol.globi.service.Dataset;
import org.eol.globi.service.DatasetCached;
import org.eol.globi.service.DatasetFactory;
import org.eol.globi.service.DatasetFinder;
import org.eol.globi.service.DatasetFinderException;
import org.eol.globi.service.DatasetImpl;
import org.joda.time.format.ISODateTimeFormat;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.TreeSet;

public class DatasetFinderLocal implements DatasetFinder {
    private final String cacheDir;

    public DatasetFinderLocal(String cacheDir) {
        this.cacheDir = cacheDir;
    }

    @Override
    public Collection<String> findNamespaces() throws DatasetFinderException {
        Collection<File> accessFiles = FileUtils.listFiles(new File(cacheDir), new FileFileFilter() {
            @Override
            public boolean accept(File file) {
                return "access.tsv".endsWith(file.getName());
            }
        }, TrueFileFilter.INSTANCE);

        Collection<String> namespaces = new TreeSet<>();
        for (File accessFile : accessFiles) {
            try {
                String[] rows = IOUtils.toString(accessFile.toURI()).split("\n");
                for (String row : rows) {
                    namespaces.add(row.split("\t")[0]);
                }
            } catch (IOException e) {
                throw new DatasetFinderException("failed to read ", e);
            }
        }

        return namespaces;
    }


    @Override
    public Dataset datasetFor(String namespace) throws DatasetFinderException {
        Dataset dataset;

        try {
            final URI sourceURI = findLastCachedDatasetURI(namespace);
            dataset = sourceURI == null ? null : DatasetFactory.datasetFor(namespace, new DatasetFinder() {
                @Override
                public Collection<String> findNamespaces() throws DatasetFinderException {
                    return Arrays.asList(namespace);
                }

                @Override
                public Dataset datasetFor(String s) throws DatasetFinderException {
                    return new DatasetWithCache(new DatasetImpl(namespace, sourceURI),
                            DatasetFinderCaching.cacheFor(namespace, cacheDir));
                }
            });
        } catch (IOException e) {
            throw new DatasetFinderException("failed to access [" + namespace + "]", e);
        }

        if (dataset == null) {
            throw new DatasetFinderException("failed to retrieve/cache dataset in namespace [" + namespace + "]");
        }

        return dataset;
    }

    private URI findLastCachedDatasetURI(String namespace) throws IOException {
        URI sourceURI = null;
        File accessFile = new File(cacheDir + "/" + namespace + "/access.tsv");
        if (accessFile.exists()) {
            String[] rows = IOUtils.toString(accessFile.toURI()).split("\n");
            for (String row : rows) {
                String[] split = row.split("\t");
                if (split.length > 3
                        && StringUtils.equalsIgnoreCase(StringUtils.trim(split[0]), namespace)) {
                    sourceURI = URI.create(split[1]);
                }
            }
        }
        return sourceURI;
    }


}
