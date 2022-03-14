/*
 * (C) Copyright 2022 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.nuxeo.compound.documents;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.nuxeo.compound.documents.CompoundDocumentConstants.COMPOUND_DOCTYPE;
import static org.nuxeo.compound.documents.CompoundDocumentConstants.COMPOUND_FOLDER_DOCTYPE;

import java.io.File;
import java.io.IOException;

import org.nuxeo.common.utils.ZipUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.runtime.api.Framework;

/** @since 2021.0 */
public final class CompoundDocumentUtils {

    private CompoundDocumentUtils() {
        // Utility class
    }

    protected static Blob makeTestZipBlob() throws IOException {
        File[] source = new File(
                CompoundDocumentUtils.class.getResource("/files/defaultCompound").getPath()).listFiles();
        File newZip = Framework.createTempFile("test", ".zip");
        ZipUtils.zip(source, newZip);
        Blob blob = Blobs.createBlob(newZip);
        blob.setFilename("test");
        return blob;
    }

    protected static void assertCompoundDocument(DocumentModel doc) {
        CoreSession session = doc.getCoreSession();
        // assert structure
        assertEquals(COMPOUND_DOCTYPE, doc.getType());
        String parent = doc.getPathAsString();
        assertEquals(COMPOUND_FOLDER_DOCTYPE, session.getDocument(new PathRef(parent + "/a")).getType());
        assertEquals(COMPOUND_FOLDER_DOCTYPE, session.getDocument(new PathRef(parent + "/a/b")).getType());

        // assert files
        assertEquals("Note", session.getDocument(new PathRef(parent + "/a.txt")).getType());
        assertEquals("Note", session.getDocument(new PathRef(parent + "/a/b.txt")).getType());
        assertEquals("Note", session.getDocument(new PathRef(parent + "/a/b/c.txt")).getType());

        // assert unallowed files are filtered
        // based on equality
        assertFalse(session.exists(new PathRef(parent + "/a/desktop.ini")));
        // based on regex match
        assertFalse(session.exists(new PathRef(parent + "/a/b/.hidden_file")));
    }

}
