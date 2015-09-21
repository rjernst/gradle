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

package org.gradle.api.internal.file.collections.jdk7;

import org.gradle.api.GradleException;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.DefaultFileVisitDetails;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.specs.Spec;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicBoolean;

public class Jdk7DirectoryWalker implements DirectoryFileTree.DirectoryWalker {
    private static final int MAX_VISIT_DEPTH = 512;

    static boolean isAllowed(FileTreeElement element, Spec<FileTreeElement> spec) {
        return spec.isSatisfiedBy(element);
    }

    @Override
    public void walkDir(final File rootDir, final RelativePath rootPath, final FileVisitor visitor, final Spec<FileTreeElement> spec, final AtomicBoolean stopFlag, final FileSystem fileSystem, final boolean postfix) {
        final ArrayDeque<FileVisitDetails> directoryDetailsHolder = new ArrayDeque<FileVisitDetails>();

        try {
            Files.walkFileTree(rootDir.toPath(), EnumSet.of(FileVisitOption.FOLLOW_LINKS), 512, new java.nio.file.FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    FileVisitDetails details = getFileVisitDetails(dir, false, attrs);
                    if (directoryDetailsHolder.size()==0 || isAllowed(details, spec)) {
                        directoryDetailsHolder.push(details);
                        if (directoryDetailsHolder.size() > 1 && !postfix) {
                            visitor.visitDir(details);
                        }
                        return checkStopFlag();
                    } else {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }

                private FileVisitResult checkStopFlag() {
                    return stopFlag.get() ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (attrs.isSymbolicLink()) {
                        // when FileVisitOption.FOLLOW_LINKS, we only get here when link couldn't be followed
                        throw new GradleException(String.format("Could not list contents of '%s'. Couldn't follow symbolic link.", file));
                    }
                    FileVisitDetails details = getFileVisitDetails(file, true, attrs);
                    if (isAllowed(details, spec)) {
                        visitor.visitFile(details);
                    }
                    return checkStopFlag();
                }

                private FileVisitDetails getFileVisitDetails(Path file, boolean isFile, BasicFileAttributes attrs) {
                    File child = new StatCachingFile(file.toAbsolutePath().toString(), !isFile, attrs.lastModifiedTime().toMillis(), attrs.size());
                    FileVisitDetails dirDetails = directoryDetailsHolder.peek();
                    RelativePath childPath = dirDetails != null ? dirDetails.getRelativePath().append(isFile, child.getName()) : rootPath;
                    return new DefaultFileVisitDetails(child, childPath, stopFlag, fileSystem, fileSystem);
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    if (exc != null && !(exc instanceof FileSystemLoopException)) {
                        throw new GradleException(String.format("Could not read path '%s'.", file), exc);
                    }
                    return checkStopFlag();
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (exc != null) {
                        if (!(exc instanceof FileSystemLoopException)) {
                            throw new GradleException(String.format("Could not read directory path '%s'.", dir), exc);
                        }
                    } else {
                        if (postfix) {
                            FileVisitDetails details = directoryDetailsHolder.peek();
                            if (directoryDetailsHolder.size() > 1 && details != null) {
                                visitor.visitDir(details);
                            }
                        }
                    }
                    directoryDetailsHolder.pop();
                    return checkStopFlag();
                }
            });
        } catch (IOException e) {
            throw new GradleException(String.format("Could not list contents of directory '%s'.", rootDir), e);
        }
    }

    static class StatCachingFile extends File {
        private final boolean isDirectory;
        private final long lastModified;
        private final long length;
        private final int hashCode;

        StatCachingFile(String absolutePathname, boolean isDirectory, long lastModified, long length) {
            super(absolutePathname);
            this.isDirectory = isDirectory;
            this.lastModified = lastModified;
            this.length = length;
            this.hashCode = super.hashCode();
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public boolean isDirectory() {
            return isDirectory;
        }

        @Override
        public boolean isFile() {
            return !isDirectory;
        }

        @Override
        public long lastModified() {
            return lastModified;
        }

        @Override
        public long length() {
            return length;
        }

        @Override
        public File getAbsoluteFile() {
            return this;
        }

        @Override
        public File getCanonicalFile() throws IOException {
            return new StatCachingFile(getCanonicalPath(), isDirectory, lastModified, length);
        }

        @Override
        public boolean canRead() {
            return true;
        }

        // replace this class with java.io.File in Java serialization
        private Object writeReplace() throws java.io.ObjectStreamException {
            return new File(getPath());
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

}
