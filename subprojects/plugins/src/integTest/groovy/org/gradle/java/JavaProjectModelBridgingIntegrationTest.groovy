/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.java

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.EnableModelDsl

class JavaProjectModelBridgingIntegrationTest extends AbstractIntegrationSpec {
    def "Java plugin source sets are visible in the software model as binaries and source sets"() {
        EnableModelDsl.enable(executer)

        buildFile << '''
plugins {
    id 'java'
}

model {
    tasks {
        verify(Task) {
            doLast {
                def binaries = $('binaries')
                assert binaries.size() == 2
                assert binaries.mainClasses instanceof ClassDirectoryBinarySpec
                assert binaries.mainClasses.buildTask.name == 'classes'
                assert binaries.testClasses instanceof ClassDirectoryBinarySpec
                assert binaries.testClasses.buildTask.name == 'testClasses'
                def sources = $('sources')
                assert sources.size() == 4
                assert sources.withType(JavaSourceSet).size() == 2
                assert sources.withType(JvmResourceSet).size() == 2
            }
        }
    }
}
'''

        expect:
        succeeds("verify")
    }

    def "rule can configure Java plugin source set input and output directories using the associated ClassDirectoryBinary"() {
        buildFile << """
plugins {
    id 'java'
}

sourceSets.main.java.srcDirs = ['before1']
sourceSets.main.resources.srcDirs = ['before1']

afterEvaluate {
    sourceSets.main.java.srcDir 'before2'
    sourceSets.main.resources.srcDir 'before2'
}

model {
    binaries.mainClasses {
        inputs.withType(JavaSourceSet) {
            assert source.srcDirs.name == ['before1', 'before2']
            source.srcDirs = ['src']
        }
        inputs.withType(JvmResourceSet) {
            assert source.srcDirs.name == ['before1', 'before2']
            source.srcDirs = ['res']
        }
        classesDir = file('out/classes')
        resourcesDir = classesDir
    }
}
"""

        file('src/Thing.java') << "class Thing { }"
        file('res/file.txt') << "resource"

        when:
        run("classes")

        then:
        file('build/classes').assertDoesNotExist()
        file('build/resources').assertDoesNotExist()
        file('out/classes/Thing.class').assertIsFile()
        file('out/classes/file.txt').assertIsFile()
    }
}
