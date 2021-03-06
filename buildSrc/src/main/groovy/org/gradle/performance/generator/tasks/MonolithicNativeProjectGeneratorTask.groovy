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

package org.gradle.performance.generator.tasks

import org.gradle.performance.generator.*

class MonolithicNativeProjectGeneratorTask extends ProjectGeneratorTask {

    def generateRootProject() {
        super.generateRootProject()

        generatePrebuiltLibrarySource()
        generateCommonLibrarySource()
    }

    void generatePrebuiltLibrarySource() {
        templateArgs.prebuiltLibraries.times { prebuiltLib ->
            rootProject.sourceFiles.times { sourceIdx ->
                def fileArgs = [ sourceIdx: sourceIdx, offset: (prebuiltLib+1)*rootProject.sourceFiles ]
                generateWithTemplate(destDir, "prebuilt/lib${prebuiltLib}/include/header${sourceIdx}.h", "native-monolithic/src/prebuilt.h", fileArgs)
            }
        }
    }

    void generateCommonLibrarySource() {
        rootProject.sourceFiles.times { sourceIdx ->
            def fileArgs = [ sourceIdx: sourceIdx ]
            generateWithTemplate(destDir, "common/include/header${sourceIdx}.h", "native-monolithic/src/common.h", fileArgs)
        }
    }

    void generateProjectSource(File projectDir, TestProject testProject, Map args) {
        generateProjectSource(projectDir, "h", testProject, args)
        generateProjectSource(projectDir, "c", testProject, args)
        generateProjectSource(projectDir, "cpp", testProject, args)
        projectDir.mkdirs()
    }

    void generateProjectSource(File projectDir, String sourceLang, TestProject testProject, Map args) {
        testProject.sourceFiles.times { sourceIdx ->
            def fileArgs = args + [ sourceIdx: sourceIdx, offset: (sourceIdx+1)*args.functionCount ]
            generateWithTemplate(destDir, "modules/${testProject.name}/src/src${sourceIdx}_${sourceLang}.${sourceLang}", "native-monolithic/src/src.${sourceLang}", fileArgs)
            generateWithTemplate(destDir, "modules/${testProject.name}/src/unused${sourceIdx}.${sourceLang}", "native-monolithic/src/unused.c", fileArgs)
        }
    }
}
