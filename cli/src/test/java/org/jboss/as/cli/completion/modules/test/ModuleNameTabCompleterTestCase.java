/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016, JBoss Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.cli.completion.modules.test;

import org.jboss.as.cli.handlers.ModuleNameTabCompleter;
import org.jboss.as.cli.handlers.module.ModuleConfigImpl;
import org.jboss.staxmapper.FormattingXMLStreamWriter;
import org.junit.After;
import org.junit.Test;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 *
 * @author Bartosz Spyrko-Smietanko
 */
public class ModuleNameTabCompleterTestCase {

    public static final String MODULES_DIR = "test-modules";

    @After
    public void tearDown() {
        final File modules = new File(MODULES_DIR);
        if (modules.exists()) {
            remove(modules);
        }
    }

    private void remove(File p) {
        for (File child : p.listFiles()) {
            if (child.isDirectory()) {
                remove(child);
            } else {
                child.delete();
            }
        }

        p.delete();
    }

    @Test
    public void listAllFoldersIfBufferIsNull() throws Exception {
        createModules("org", "com");

        List<String> suggestions = suggestions(null);

        assertEquals(Arrays.asList("com", "org"), suggestions);
    }

    @Test
    public void listAllFoldersIfBufferIsEmpty() throws Exception {
        createModules("org", "com");

        List<String> suggestions = suggestions("");

        assertEquals(Arrays.asList("com", "org"), suggestions);
    }

    @Test
    public void listMatchingFoldersIfBufferIsNotEmpty() throws Exception {
        createModules("org", "foo", "foo2");

        List<String> suggestions = suggestions("f");

        assertEquals(Arrays.asList("foo", "foo2"), suggestions);
    }

    @Test
    public void ignoreLayersFolder() throws Exception {
        createModules("org", "com");
        new File(MODULES_DIR + "/system").mkdirs();

        List<String> suggestions = suggestions(null);

        assertEquals(Arrays.asList("com", "org"), suggestions);
    }

    @Test
    public void returnEmptyListIfNoMatchesFound() throws Exception {
        createModules("com");

        List<String> suggestions = suggestions("foo");

        assertEquals(Collections.emptyList(), suggestions);
    }

    @Test
    public void listBaseLayerModulesIfBufferIsEmpty() throws Exception {
        createLayerModules("base", "org", "com");

        List<String> suggestions = suggestions(null);

        assertEquals(Arrays.asList("com", "org"), suggestions);
    }

    @Test
    public void listNestedFolderIfBufferEndsWithSeparator() throws Exception {
        createModules("org/root");
        createLayerModules("base", "org/layer");

        List<String> suggestions = suggestions("org.");

        assertEquals(Arrays.asList("org.layer", "org.root"), suggestions);
    }

    @Test
    public void listFoldersFromMultipleLayers() throws Exception {
        createLayerModules("base", "layer1");
        createLayerModules("another", "layer2");

        List<String> suggestions = suggestions(null);

        assertEquals(Arrays.asList("layer1", "layer2"), suggestions);
    }

    @Test
    public void ignoreDuplicatedFolderNames() throws Exception {
        createLayerModules("base", "module");
        createLayerModules("another", "module");

        List<String> suggestions = suggestions(null);

        assertEquals(Arrays.asList("module"), suggestions);
    }

    @Test
    public void escapeSpacesInFileNames() throws Exception {
        createModules("module 1", "module 2");

        List<String> suggestions = suggestions(null);

        assertEquals(Arrays.asList("module\\ 1", "module\\ 2"), suggestions);
    }

    @Test
    public void shouldNotIncludeSlotName() throws Exception {
        createModules("foo/bar");

        List<String> suggestions = suggestions("foo.bar.");

        assertEquals(Collections.emptyList(), suggestions);
    }

    private static void createModules(String... names) throws IOException, XMLStreamException {
        doCreateModule(MODULES_DIR + "/", names);
    }

    private static void createLayerModules(String layerName, String... names) throws IOException, XMLStreamException {
        doCreateModule(MODULES_DIR + "/system/layers/" + layerName + "/", names);
    }

    private static void doCreateModule(String prefix, String[] names) throws IOException, XMLStreamException {
        for (String name : names) {
            new File(prefix + name + "/main").mkdirs();
            final File file = new File(prefix + name + "/main/module.xml");
            file.createNewFile();

            try(FileOutputStream fos = new FileOutputStream(file);
                OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8")) {

                final FormattingXMLStreamWriter xmlWriter = new FormattingXMLStreamWriter(XMLOutputFactory.newInstance().createXMLStreamWriter(writer));
                final ModuleConfigImpl moduleConfig = new ModuleConfigImpl(name.replace('/', '.'));
                moduleConfig.writeContent(xmlWriter, moduleConfig);
            }
        }
    }

    private List<String> suggestions(String buffer) {
        final ModuleNameTabCompleter completer = new ModuleNameTabCompleter(MODULES_DIR);

        final ArrayList<String> candidates = new ArrayList<>();
        completer.complete(null, buffer, 0, candidates);

        return candidates;
    }
}