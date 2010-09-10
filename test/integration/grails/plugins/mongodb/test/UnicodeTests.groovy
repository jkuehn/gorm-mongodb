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

import org.acme.Task

/**
 *
 * @author Cory Hacking
 */
class UnicodeTests extends GroovyTestCase {

    void testSaveUnicodeData() {
        def id = "unicode-test-正規"

        def t = Task.get(id)
        if (t) {
            t.delete()
        }

        t = new Task()

        t.taskId = id
        t.name = "»» 正規表達式 ... \u6B63"
        t.save()

        assertNotNull "should have no errors on new unicode task", ((boolean)t.errors?.allErrors)

        def t2 = Task.get(id)

        assertNotNull "should have read unicode project", t2
        assertEquals "Unicode name should be the same", t.name, t2.name

        t2.delete()
    }
}
