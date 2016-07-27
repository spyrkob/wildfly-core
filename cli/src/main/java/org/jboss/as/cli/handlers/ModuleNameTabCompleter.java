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
package org.jboss.as.cli.handlers;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.EscapeSelector;
import org.jboss.as.cli.Util;
import org.jboss.logging.Logger;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 *
 * @author Bartosz Spyrko-Smietanko
 */
public class ModuleNameTabCompleter implements CommandLineCompleter {
    private static final Logger log = Logger.getLogger(ModuleNameTabCompleter.class);

    private static final EscapeSelector ESCAPE_SELECTOR = ch -> ch == '\\' || ch == ' ' || ch == '"';
    private static final char MODULE_NAME_SEPARATOR = '.';

    private final File modulesRoot;

    public ModuleNameTabCompleter(String modulesRoot) {
        this.modulesRoot = new File(modulesRoot);
    }

    @Override
    public int complete(CommandContext ctx, String buffer, int cursor, List<String> candidates) {
        final Set<String> moduleNames = findModules(modulesRoot);

        final String prefix = buffer==null?"":buffer;

        moduleNames.stream()
                .map(moduleName->Util.escapeString(moduleName, ESCAPE_SELECTOR))
                .filter(moduleName->moduleName.startsWith(prefix))
                .map(moduleName-> firstLevelOfModuleName(prefix, moduleName))
                .distinct()
                .forEachOrdered(candidates::add);

        return 0;
    }

    // if the moduleName has multiple levels after the prefix, only use the first level
    private String firstLevelOfModuleName(String prefix, String moduleName) {
        final int separatorPosition = moduleName.indexOf(MODULE_NAME_SEPARATOR, prefix.length());
        if (separatorPosition > 0) {
            return moduleName.substring(0, separatorPosition + 1); // include trailing separator
        } else {
            return moduleName;
        }
    }

    // recursively look for module.xml files and read module name attributes
    private Set<String> findModules(File root) {
        final File[] children = root.listFiles((f)->f.isDirectory());
        final TreeSet<String> names = new TreeSet<>(); // TreeSet to guarantee ordering

        for (File child : children) {
            findModules(names, child);
        }

        return names;
    }

    private void findModules(Set<String> moduleNames, File currentDirectory) {
        final File[] children = currentDirectory.listFiles();

        for (File child : children) {
            if (child.isDirectory()) {
                findModules(moduleNames, child);
            } else if (child.getName().equals("module.xml")) {
                readModuleName(child).ifPresent(moduleNames::add);
            }
        }
    }

    private Optional<String> readModuleName(File moduleFile) {
        final XMLInputFactory f = XMLInputFactory.newFactory();
        XMLStreamReader xmlReader = null;
        try (final FileInputStream stream = new FileInputStream(moduleFile)) {
            xmlReader = f.createXMLStreamReader(stream);

            while(xmlReader.hasNext()) {
                // find module tag
                xmlReader.nextTag();
                if (xmlReader.getEventType() == XMLStreamConstants.START_ELEMENT && "module".equals(xmlReader.getLocalName())) {
                    // and read name attribute
                    final int attrCount = xmlReader.getAttributeCount();
                    for (int i=0; i< attrCount; i++) {
                        final String attrName = xmlReader.getAttributeLocalName(i);
                        if ("name".equals(attrName)) {
                            return Optional.of(xmlReader.getAttributeValue(i));
                        }
                    }
                    return Optional.empty();
                }
            }
        } catch (XMLStreamException | IOException e) {
            // ignore modules that fail to parse - if there's a problem with the module, it will fail when loaded
            log.debug("Failed to parse module descriptor " + moduleFile.getAbsolutePath(), e);
        } finally {
            if (xmlReader != null) {
                try {
                    xmlReader.close();
                } catch (XMLStreamException e) {
                    log.debug("Failed to close XML stream", e);
                }
            }
        }
        return Optional.empty();
    }
}
