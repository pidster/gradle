/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.externalresource.local.ivy;

import org.apache.ivy.core.module.id.ArtifactRevisionId;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetaData;
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator;
import org.gradle.api.internal.externalresource.local.CompositeLocallyAvailableResourceFinder;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinder;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinderSearchableFileStoreAdapter;
import org.gradle.api.internal.filestore.FileStoreSearcher;
import org.gradle.internal.Factory;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

public class LocallyAvailableResourceFinderFactory implements Factory<LocallyAvailableResourceFinder<ArtifactRevisionId>> {
    private final File rootCachesDirectory;
    private final LocalMavenRepositoryLocator localMavenRepositoryLocator;
    private final FileStoreSearcher<ArtifactRevisionId> fileStore;

    public LocallyAvailableResourceFinderFactory(
            ArtifactCacheMetaData artifactCacheMetaData, LocalMavenRepositoryLocator localMavenRepositoryLocator, FileStoreSearcher<ArtifactRevisionId> fileStore) {
        this.rootCachesDirectory = artifactCacheMetaData.getCacheDir().getParentFile();
        this.localMavenRepositoryLocator = localMavenRepositoryLocator;
        this.fileStore = fileStore;
    }

    public LocallyAvailableResourceFinder<ArtifactRevisionId> create() {
        List<LocallyAvailableResourceFinder<ArtifactRevisionId>> finders = new LinkedList<LocallyAvailableResourceFinder<ArtifactRevisionId>>();

        // Order is important here, because they will be searched in that order
        
        // The current filestore
        finders.add(new LocallyAvailableResourceFinderSearchableFileStoreAdapter<ArtifactRevisionId>(fileStore));
        
        // rc-1, 1.0
        addForPattern(finders, "artifacts-13", "filestore/[organisation]/[module](/[branch])/[revision]/[type]/*/[artifact]-[revision](-[classifier])(.[ext])");

        // Milestone 8 and 9
        addForPattern(finders, "artifacts-8", "filestore/[organisation]/[module](/[branch])/[revision]/[type]/*/[artifact]-[revision](-[classifier])(.[ext])");

        // Milestone 7
        addForPattern(finders, "artifacts-7", "artifacts/*/[organisation]/[module](/[branch])/[revision]/[type]/[artifact]-[revision](-[classifier])(.[ext])");

        // Milestone 6
        addForPattern(finders, "artifacts-4", "[organisation]/[module](/[branch])/*/[type]s/[artifact]-[revision](-[classifier])(.[ext])");
        addForPattern(finders, "artifacts-4", "[organisation]/[module](/[branch])/*/pom.originals/[artifact]-[revision](-[classifier])(.[ext])");

        // Milestone 3
        addForPattern(finders, "../cache", "[organisation]/[module](/[branch])/[type]s/[artifact]-[revision](-[classifier])(.[ext])");

        // Maven local
        addForPattern(finders, localMavenRepositoryLocator.getLocalMavenRepository(), "[organisation-path]/[module]/[revision]/[artifact]-[revision](-[classifier])(.[ext])");

        return new CompositeLocallyAvailableResourceFinder<ArtifactRevisionId>(finders);
    }

    private void addForPattern(List<LocallyAvailableResourceFinder<ArtifactRevisionId>> finders, String path, String pattern) {
        addForPattern(finders, new File(rootCachesDirectory, path), pattern);
    }

    private void addForPattern(List<LocallyAvailableResourceFinder<ArtifactRevisionId>> finders, File baseDir, String pattern) {
        if (baseDir.exists()) {
            finders.add(new PatternBasedLocallyAvailableResourceFinder(baseDir, pattern));
        }
    }

}
