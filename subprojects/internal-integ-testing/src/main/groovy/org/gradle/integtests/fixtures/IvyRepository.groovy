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
package org.gradle.integtests.fixtures

import java.util.regex.Pattern
import junit.framework.AssertionFailedError

import org.gradle.util.TestFile
import org.gradle.util.hash.HashUtil

class IvyRepository {
    final TestFile rootDir

    IvyRepository(TestFile rootDir) {
        this.rootDir = rootDir
    }

    URI getUri() {
        return rootDir.toURI()
    }

    IvyModule module(String organisation, String module, Object revision = '1.0') {
        def moduleDir = rootDir.file("$organisation/$module/$revision")
        return new IvyModule(moduleDir, organisation, module, revision as String)
    }
}

class IvyModule {
    final TestFile moduleDir
    final String organisation
    final String module
    final String revision
    final List dependencies = []
    final Map<String, Map> configurations = [:]
    final List artifacts = []
    String status = "integration"
    int publishCount

    IvyModule(TestFile moduleDir, String organisation, String module, String revision) {
        this.moduleDir = moduleDir
        this.organisation = organisation
        this.module = module
        this.revision = revision
        artifact([:])
        configurations['runtime'] = [extendsFrom: [], transitive: true]
        configurations['default'] = [extendsFrom: ['runtime'], transitive: true]
    }

    /**
     * Adds an additional artifact to this module.
     * @param options Can specify any of name, type or classifier
     * @return this
     */
    IvyModule artifact(Map<String, ?> options) {
        artifacts << [name: options.name ?: module, type: options.type ?: 'jar', classifier: options.classifier ?: null]
        return this
    }

    IvyModule dependsOn(String organisation, String module, String revision) {
        dependencies << [organisation: organisation, module: module, revision: revision]
        return this
    }

    IvyModule nonTransitive(String config) {
        configurations[config].transitive = false
        return this
    }

    IvyModule withStatus(String status) {
        this.status = status;
        return this
    }

    TestFile getIvyFile() {
        return moduleDir.file("ivy-${revision}.xml")
    }

    TestFile getJarFile() {
        return moduleDir.file("$module-${revision}.jar")
    }

    TestFile sha1File(File file) {
        return moduleDir.file("${file.name}.sha1")
    }

    /**
     * Publishes ivy.xml plus all artifacts with different content to previous publication.
     */
    IvyModule publishWithChangedContent() {
        publishCount++
        publish()
    }

    /**
     * Publishes ivy.xml plus all artifacts
     */
    IvyModule publish() {
        moduleDir.createDir()

        ivyFile.text = """<?xml version="1.0" encoding="UTF-8"?>
<ivy-module version="1.0" xmlns:m="http://ant.apache.org/ivy/maven">
	<info organisation="${organisation}"
		module="${module}"
		revision="${revision}"
		status="${status}"
	/>
	<configurations>"""
        configurations.each { name, config ->
            ivyFile << "<conf name='$name' visibility='public'"
            if (config.extendsFrom) {
                ivyFile << " extends='${config.extendsFrom.join(',')}'"
            }
            if (!config.transitive) {
                ivyFile << " transitive='false'"
            }
            ivyFile << "/>"
        }
	ivyFile << """</configurations>
	<publications>
"""
        artifacts.each { artifact ->
            def artifactFile = file(artifact)
            artifactFile << "${artifactFile.name} : $publishCount"
            publishSha1(artifactFile)
            ivyFile << """<artifact name="${artifact.name}" type="${artifact.type}" ext="${artifact.type}" conf="*" m:classifier="${artifact.classifier ?: ''}"/>
"""
        }
        ivyFile << """
	</publications>
	<dependencies>
"""
        dependencies.each { dep ->
            ivyFile << """<dependency org="${dep.organisation}" name="${dep.module}" rev="${dep.revision}"/>
"""
        }
        ivyFile << """
    </dependencies>
</ivy-module>
        """
        publishSha1(ivyFile)

        publishSha1(ivyFile)
        return this
    }

    private TestFile file(def artifact) {
        return moduleDir.file("${artifact.name}-${revision}${artifact.classifier ? '-' + artifact.classifier : ''}.${artifact.type}")
    }

    private publishSha1(TestFile file) {
        sha1File(file).text = getHash(file, "SHA1")
    }

    /**
     * Asserts that exactly the given artifacts have been published.
     */
    void assertArtifactsPublished(String... names) {
        Set allFileNames = [];
        for (name in names) {
            allFileNames += [name, "${name}.sha1"]
        }
        assert moduleDir.list() as Set == allFileNames
    }

    void assertChecksumPublishedFor(TestFile testFile) {
        def sha1File = sha1File(testFile)
        sha1File.assertIsFile()
        assert sha1File.text == getHash(testFile, "SHA1")
    }

    public String getHash(File file, String algorithm) {
        return HashUtil.createHash(file, algorithm).asHexString()
    }

    IvyDescriptor getIvy() {
        return new IvyDescriptor(ivyFile)
    }

    public expectIvyHead(HttpServer server, prefix = null) {
        server.expectHead(ivyPath(prefix), ivyFile)
    }

    public expectIvyGet(HttpServer server, prefix = null) {
        server.expectGet(ivyPath(prefix), ivyFile)
    }

    public ivyPath(prefix = null) {
        path(prefix, ivyFile.name)
    }

    public expectIvySha1Get(HttpServer server, prefix = null) {
        server.expectGet(ivySha1Path(prefix), sha1File(ivyFile))
    }

    public ivySha1Path(prefix = null) {
        ivyPath(prefix) + ".sha1"
    }

    public expectArtifactHead(HttpServer server, prefix = null) {
        server.expectHead(artifactPath(prefix), jarFile)
    }

    public expectArtifactGet(HttpServer server, prefix = null) {
        server.expectGet(artifactPath(prefix), jarFile)
    }

    public artifactPath(prefix = null) {
        path(prefix, jarFile.name)
    }

    public expectArtifactSha1Get(HttpServer server, prefix = null) {
        server.expectGet(artifactSha1Path(prefix), sha1File(jarFile))
    }

    public artifactSha1Path(prefix = null) {
        artifactPath(prefix) + ".sha1"
    }

    public path(prefix = null, String filename) {
        "${prefix == null ? "" : prefix}/${organisation}/${module}/${revision}/${filename}"
    }
}

class IvyDescriptor {
    final Map<String, IvyConfiguration> configurations = [:]

    IvyDescriptor(File ivyFile) {
        def ivy = new XmlParser().parse(ivyFile)
        ivy.dependencies.dependency.each { dep ->
            def configName = dep.@conf ?: "default"
            def matcher = Pattern.compile("(\\w+)->\\w+").matcher(configName)
            if (matcher.matches()) {
                configName = matcher.group(1)
            }
            def config = configurations[configName]
            if (!config) {
                config = new IvyConfiguration()
                configurations[configName] = config
            }
            config.addDependency(dep.@org, dep.@name, dep.@rev)
        }
    }
}

class IvyConfiguration {
    final dependencies = []

    void addDependency(String org, String module, String revision) {
        dependencies << [org: org, module: module, revision: revision]
    }

    void assertDependsOn(String org, String module, String revision) {
        def dep = [org: org, module: module, revision: revision]
        if (!dependencies.find { it == dep}) {
            throw new AssertionFailedError("Could not find expected dependency $dep. Actual: $dependencies")
        }
    }
}
