/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.component.external.model

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory
import org.gradle.internal.component.external.descriptor.Artifact
import org.gradle.internal.component.external.descriptor.Configuration
import org.gradle.internal.component.external.descriptor.DefaultExclude
import org.gradle.internal.component.model.ComponentResolveMetadata
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.component.model.ModuleSource
import org.gradle.internal.hash.HashValue
import org.gradle.util.TestUtil

class DefaultMutableIvyModuleResolveMetadataTest extends AbstractMutableModuleComponentResolveMetadataTest {
    private final IvyMutableModuleMetadataFactory ivyMetadataFactory = new IvyMutableModuleMetadataFactory(new DefaultImmutableModuleIdentifierFactory(), TestUtil.attributesFactory())

    @Override
    AbstractMutableModuleComponentResolveMetadata createMetadata(ModuleComponentIdentifier id, List<Configuration> configurations, List<DependencyMetadata> dependencies) {
        ivyMetadataFactory.create(id, dependencies, configurations, [], []) as AbstractMutableModuleComponentResolveMetadata
    }

    @Override
    AbstractMutableModuleComponentResolveMetadata createMetadata(ModuleComponentIdentifier id) {
        ivyMetadataFactory.create(id) as AbstractMutableModuleComponentResolveMetadata
    }

    def "initialises values from descriptor state and defaults"() {
        def id = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "version")
        configuration("runtime", [])
        configuration("default", ["runtime"])
        def a1 = artifact("runtime.jar", "runtime")
        def a2 = artifact("api.jar", "default")

        expect:
        def metadata = ivyMetadataFactory.create(id, [], configurations, [a1, a2], [])
        metadata.id == id
        metadata.branch == null

        and:
        metadata.contentHash == AbstractMutableModuleComponentResolveMetadata.EMPTY_CONTENT
        metadata.source == null
        metadata.artifactDefinitions.size() == 2
        metadata.excludes.empty

        and:
        def immutable = metadata.asImmutable()
        immutable != metadata
        immutable.id == id
        immutable.source == null
        immutable.statusScheme == ComponentResolveMetadata.DEFAULT_STATUS_SCHEME
        immutable.branch == null
        immutable.excludes.empty
        immutable.configurationNames == ["runtime", "default"] as Set
        def runtime = immutable.getConfiguration("runtime")
        runtime.artifacts.name.name == ["runtime.jar"]
        runtime.excludes.empty
        def defaultConfig = immutable.getConfiguration("default")
        defaultConfig.hierarchy == ["default", "runtime"]
        defaultConfig.transitive
        defaultConfig.visible
        defaultConfig.artifacts.name.name == ["api.jar", "runtime.jar"]
        defaultConfig.excludes.empty

        and:
        def copy = immutable.asMutable()
        copy != metadata
        copy.id == id
        copy.source == null
        copy.statusScheme == ComponentResolveMetadata.DEFAULT_STATUS_SCHEME
        copy.branch == null
        copy.artifactDefinitions.size() == 2
        copy.excludes.empty
    }

    def "artifacts include union of those inherited from other configurations"() {
        given:
        configuration("compile")
        configuration("runtime", ["compile"])
        def a1 = artifact("one", "runtime")
        def a2 = artifact("two", "runtime", "compile")
        def a3 = artifact("three", "compile")

        def metadata = ivyMetadataFactory.create(id, [], configurations, [a1, a2, a3], [])

        expect:
        def immutable = metadata.asImmutable()
        immutable.getConfiguration("compile").artifacts.name.name == ["two", "three"]
        immutable.getConfiguration("runtime").artifacts.name.name == ["one", "two", "three"]
    }

    def "can override values from descriptor"() {
        def id = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "version")
        def newId = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "1.2")
        def source = Stub(ModuleSource)
        def contentHash = new HashValue("123")
        def excludes = [new DefaultExclude(DefaultModuleIdentifier.newId("group", "name"))]

        when:
        def metadata = ivyMetadataFactory.create(id, [], [], [], excludes)
        metadata.id = newId
        metadata.source = source
        metadata.status = "3"
        metadata.branch = "release"
        metadata.changing = true
        metadata.missing = true
        metadata.statusScheme = ["1", "2", "3"]
        metadata.contentHash = contentHash

        then:
        metadata.id == newId
        metadata.moduleVersionId == DefaultModuleVersionIdentifier.newId(newId)
        metadata.source == source
        metadata.changing
        metadata.missing
        metadata.status == "3"
        metadata.branch == "release"
        metadata.statusScheme == ["1", "2", "3"]
        metadata.contentHash == contentHash
        metadata.excludes == excludes

        def immutable = metadata.asImmutable()
        immutable != metadata
        immutable.id == newId
        immutable.moduleVersionId == DefaultModuleVersionIdentifier.newId(newId)
        immutable.source == source
        immutable.status == "3"
        immutable.branch == "release"
        immutable.changing
        immutable.missing
        immutable.statusScheme == ["1", "2", "3"]
        immutable.contentHash == contentHash
        immutable.excludes == excludes

        def copy = immutable.asMutable()
        copy != metadata
        copy.id == newId
        copy.moduleVersionId == DefaultModuleVersionIdentifier.newId(newId)
        copy.source == source
        copy.status == "3"
        copy.branch == "release"
        copy.changing
        copy.missing
        copy.statusScheme == ["1", "2", "3"]
        copy.contentHash == contentHash
        copy.excludes == excludes
    }

    def "making changes to copy does not affect original"() {
        def id = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "version")
        def newId = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "1.2")
        def source = Stub(ModuleSource)

        when:
        def metadata = ivyMetadataFactory.create(id, [], [], [], [])
        def immutable = metadata.asImmutable()
        def copy = immutable.asMutable()
        copy.id = newId
        copy.source = source
        copy.statusScheme = ["2", "3"]
        def immutableCopy = copy.asImmutable()

        then:
        metadata.id == id
        metadata.source == null
        metadata.statusScheme == ComponentResolveMetadata.DEFAULT_STATUS_SCHEME

        immutable.id == id
        immutable.source == null
        immutable.statusScheme == ComponentResolveMetadata.DEFAULT_STATUS_SCHEME

        copy.id == newId
        copy.source == source
        copy.statusScheme == ["2", "3"]

        immutableCopy.id == newId
        immutableCopy.source == source
        immutableCopy.statusScheme == ["2", "3"]
    }

    def "making changes to original does not affect copy"() {
        def id = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "version")
        def newId = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "1.2")
        def source = Stub(ModuleSource)

        when:
        def metadata = ivyMetadataFactory.create(id, [], [], [], [])
        def immutable = metadata.asImmutable()

        metadata.id = newId
        metadata.source = source
        metadata.statusScheme = ["1", "2"]

        def immutableCopy = metadata.asImmutable()

        then:
        metadata.id == newId
        metadata.source == source
        metadata.statusScheme == ["1", "2"]

        immutable.id == id
        immutable.source == null
        immutable.statusScheme == ComponentResolveMetadata.DEFAULT_STATUS_SCHEME

        immutableCopy.id == newId
        immutableCopy.source == source
        immutableCopy.statusScheme == ["1", "2"]
    }

    def "treats ivy configurations as variants when checking if a variant exists"() {
        when:
        configuration("compile")
        def metadata = getMetadata()

        then:
        metadata.definesVariant("compile")
        !metadata.definesVariant("runtime")
    }

    def exclude(String group, String module, String... confs) {
        def exclude = new DefaultExclude(DefaultModuleIdentifier.newId(group, module), confs, "whatever")
        return exclude
    }

    def artifact(String name, String... confs) {
        def artifact = new Artifact(new DefaultIvyArtifactName(name, "type", "ext", "classifier"), confs as Set<String>)
        return artifact
    }
}
