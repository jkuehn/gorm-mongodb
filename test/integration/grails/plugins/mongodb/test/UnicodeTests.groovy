/*
 * Copyright 2009 the original author or authors.
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
package grails.plugins.mongodb.test

import org.acme.Project

/**
 *
 * @author Cory Hacking
 */
class UnicodeTests extends GroovyTestCase {

    void testSaveUnicodeData() {
        def id = "unicode-test-正規"

        def p = Project.get(id)
        if (p) {
            p.delete()
        }

        p = new Project()

        p.id = id
        p.name = "»» 正規表達式 ... \u6B63"
        p.save()

        assertNotNull "should have saved new unicode project", p.id

        def p2 = Project.get(id)

        assertNotNull "should have read unicode project", p2
        assertEquals "Unicode name should be the same", p.name, p2.name

        p2.delete()
    }
}
