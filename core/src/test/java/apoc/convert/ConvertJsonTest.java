/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package apoc.convert;

import static apoc.convert.Json.NODE;
import static apoc.convert.Json.RELATIONSHIP;
import static apoc.util.JsonUtil.PATH_OPTIONS_ERROR_MESSAGE;
import static apoc.util.MapUtil.map;
import static apoc.util.TestUtil.testCall;
import static apoc.util.TestUtil.testResult;
import static java.util.Arrays.asList;
import static org.junit.Assert.*;

import apoc.util.MapUtil;
import apoc.util.TestUtil;
import apoc.util.Util;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import junit.framework.TestCase;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.assertj.core.util.Arrays;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.neo4j.graphdb.Entity;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.QueryExecutionException;
import org.neo4j.graphdb.Result;
import org.neo4j.test.rule.DbmsRule;
import org.neo4j.test.rule.ImpermanentDbmsRule;

public class ConvertJsonTest {
    private static final Map<String, Object> EXPECTED_COLUMNS_MAP =
            Map.of("row", Map.of("poiType", "Governorate", "poi", 772L), "col2", Map.of("_id", "772col2"));
    // json extracted from issue #1445
    private static final String JSON =
            "{\"columns\":{\"row\":{\"poiType\":\"Governorate\",\"poi\":772},\"col2\":{\"_id\":\"772col2\"}}}";

    public static final List<Map<String, Object>> EXPECTED_PATH = List.of(EXPECTED_COLUMNS_MAP);
    public static final List<Object> EXPECTED_PATH_WITH_NULLS =
            Arrays.asList(new Object[] {EXPECTED_COLUMNS_MAP, null, null, null});
    public static final List<String> EXPECTED_AS_PATH_LIST = List.of("$['columns']");

    @Rule
    public DbmsRule db = new ImpermanentDbmsRule();

    @Before
    public void setUp() {
        TestUtil.registerProcedure(db, Json.class);
    }

    @After
    public void teardown() {
        db.shutdown();
    }

    @After
    public void clear() {
        db.executeTransactionally("MATCH (n) DETACH DELETE n;");
    }

    @Test
    public void testJsonPath() {
        // -- json.path
        testCall(
                db,
                "RETURN apoc.json.path($json, '$..columns') AS path",
                Map.of("json", JSON),
                (row) -> assertEquals(EXPECTED_PATH_WITH_NULLS, row.get("path")));

        testCall(
                db,
                "RETURN apoc.json.path($json, '$..columns', ['AS_PATH_LIST']) AS path",
                Map.of("json", JSON),
                (row) -> assertEquals(EXPECTED_AS_PATH_LIST, row.get("path")));

        testCall(
                db,
                "RETURN apoc.json.path($json, '$..columns', []) AS path",
                Map.of("json", JSON),
                (row) -> assertEquals(EXPECTED_PATH, row.get("path")));

        // -- convert.fromJsonList
        testCall(
                db,
                "RETURN apoc.convert.fromJsonList($json, '$..columns') AS path",
                Map.of("json", JSON),
                (row) -> assertEquals(EXPECTED_PATH_WITH_NULLS, row.get("path")));

        testCall(
                db,
                "RETURN apoc.convert.fromJsonList($json, '$..columns', ['AS_PATH_LIST']) AS path",
                Map.of("json", JSON),
                (row) -> assertEquals(EXPECTED_AS_PATH_LIST, row.get("path")));

        testCall(
                db,
                "RETURN apoc.convert.fromJsonList($json, '$..columns', []) AS path",
                Map.of("json", JSON),
                (row) -> assertEquals(List.of(EXPECTED_COLUMNS_MAP), row.get("path")));

        db.executeTransactionally("CREATE (n:JsonPathNode {prop: $prop})", Map.of("prop", JSON));

        // -- convert.getJsonProperty
        testCall(
                db,
                "MATCH (n:JsonPathNode) RETURN apoc.convert.getJsonProperty(n, 'prop', '$..columns') AS path",
                Map.of("json", JSON),
                (row) -> assertEquals(EXPECTED_PATH_WITH_NULLS, row.get("path")));

        testCall(
                db,
                "MATCH (n:JsonPathNode) RETURN apoc.convert.getJsonProperty(n, 'prop', '$..columns', ['AS_PATH_LIST']) AS path",
                Map.of("json", JSON),
                (row) -> assertEquals(EXPECTED_AS_PATH_LIST, row.get("path")));

        testCall(
                db,
                "MATCH (n:JsonPathNode) RETURN apoc.convert.getJsonProperty(n, 'prop', '$..columns', []) AS path",
                Map.of("json", JSON),
                (row) -> assertEquals(List.of(EXPECTED_COLUMNS_MAP), row.get("path")));

        // -- invalid option
        try {
            testCall(
                    db,
                    "RETURN apoc.json.path($json, '$..columns', ['INVALID']) AS path",
                    Map.of("json", JSON),
                    (row) -> fail("Should fail because of invalid pathOptions"));
        } catch (RuntimeException e) {
            assertTrue(ExceptionUtils.getStackTrace(e).contains(PATH_OPTIONS_ERROR_MESSAGE));
        }
    }

    @Test
    public void testJsonPathWithMapFunctions() {
        // apoc.convert.getJsonPropertyMap and apoc.convert.fromJsonMap must fail with "ALWAYS_RETURN_LIST" because
        // should return a Map.
        final Map<String, String> expectedMap = Map.of("_id", "772col2");
        final String expectedError =
                "It's not possible to use ALWAYS_RETURN_LIST option because the conversion should return a Map";

        testCall(
                db,
                "RETURN apoc.convert.fromJsonMap($json, '$.columns.col2') AS path",
                Map.of("json", JSON),
                (row) -> assertEquals(expectedMap, row.get("path")));
        try {
            testCall(
                    db,
                    "RETURN apoc.convert.fromJsonMap($json, '$.columns.col2', ['ALWAYS_RETURN_LIST']) AS path",
                    Map.of("json", JSON),
                    (row) -> fail("Should fail because of MismatchedInputException"));
        } catch (QueryExecutionException e) {
            assertTrue(e.getMessage().contains(expectedError));
        }

        db.executeTransactionally("CREATE (n:JsonPathNode {prop: $prop})", Map.of("prop", JSON));
        testCall(
                db,
                "MATCH (n:JsonPathNode) RETURN apoc.convert.getJsonPropertyMap(n, 'prop', '$.columns.col2') AS path",
                Map.of("json", JSON),
                (row) -> assertEquals(expectedMap, row.get("path")));
        try {
            testCall(
                    db,
                    "MATCH (n:JsonPathNode) RETURN apoc.convert.getJsonPropertyMap(n, 'prop', '$.columns.col2', ['ALWAYS_RETURN_LIST']) AS path",
                    Map.of("json", JSON),
                    (row) -> fail("Should fail because of MismatchedInputException"));
        } catch (QueryExecutionException e) {
            assertTrue(e.getMessage().contains(expectedError));
        }
    }

    @Test
    public void testToJsonList() {
        testCall(
                db, "RETURN apoc.convert.toJson([1,2,3]) as value", (row) -> assertEquals("[1,2,3]", row.get("value")));
    }

    @Test
    public void testToJsonMap() {
        testCall(
                db,
                "RETURN apoc.convert.toJson({a:42,b:\"foo\",c:[1,2,3]}) as value",
                (row) -> assertEquals("{\"a\":42,\"b\":\"foo\",\"c\":[1,2,3]}", row.get("value")));
    }

    @Test
    public void testToJsonNode() {
        testCall(db, "CREATE (a:Test {foo: 7}) RETURN apoc.convert.toJson(a) AS value", (row) -> {
            Map<String, Object> valueAsMap = Util.readMap((String) row.get("value"));
            assertJsonNode(valueAsMap, "0", List.of("Test"), Map.of("foo", 7L));
        });
    }

    @Test
    public void testToJsonWithNullValues() {
        testCall(db, "RETURN apoc.convert.toJson({a: null, b: 'myString', c: [1,'2',null]}) as value", (row) -> {
            final Map<String, Object> value = Util.fromJson((String) row.get("value"), Map.class);
            assertNull(value.get("a"));
            assertEquals("myString", value.get("b"));
            final List<Object> expected = asList(1L, "2", null);
            assertEquals(expected, value.get("c"));
        });
    }

    @Test
    public void testToJsonNodeWithoutLabel() {
        testCall(db, "CREATE (a {pippo:'pluto'}) RETURN apoc.convert.toJson(a) AS value", (row) -> {
            Map<String, Object> valueAsMap = Util.readMap((String) row.get("value"));
            assertJsonNode(valueAsMap, "0", null, Map.of("pippo", "pluto"));
        });
    }

    @Test
    public void testToJsonCollectNodes() {
        db.executeTransactionally(
                "CREATE (f:User {name:'Adam',age:42,male:true,kids:['Sam','Anna','Grace'], born:localdatetime('2015185T19:32:24'), place: point({x: 56.7, y: 12.78, z: 1.1, crs: 'wgs-84-3d'})}),(b:User {name:'Jim',age:42}),(c:User {age:12}),(d:User),(e {pippo:'pluto'})");
        String query = "MATCH (u) RETURN apoc.convert.toJson(collect(u)) as list";
        TestUtil.testCall(db, query, (row) -> {
            List<String> users = List.of("User");
            List<Object> valueAsList = Util.fromJson((String) row.get("list"), List.class);
            assertEquals(5, valueAsList.size());

            Map<String, Object> nodeOne = (Map<String, Object>) valueAsList.get(0);
            Map<String, Object> expectedMap = Map.of(
                    "name",
                    "Adam",
                    "age",
                    42L,
                    "male",
                    true,
                    "kids",
                    List.of("Sam", "Anna", "Grace"),
                    "born",
                    "2015-07-04T19:32:24",
                    "place",
                    Map.of("latitude", 12.78, "longitude", 56.7, "crs", "wgs-84-3d", "height", 1.1));
            assertJsonNode(nodeOne, "0", users, expectedMap);

            Map<String, Object> nodeTwo = (Map<String, Object>) valueAsList.get(1);
            Map<String, Object> expectedMapTwo = Map.of("name", "Jim", "age", 42L);
            assertJsonNode(nodeTwo, "1", users, expectedMapTwo);

            Map<String, Object> nodeThree = (Map<String, Object>) valueAsList.get(2);
            Map<String, Object> expectedMapThree = Map.of("age", 12L);
            assertJsonNode(nodeThree, "2", users, expectedMapThree);

            Map<String, Object> nodeFour = (Map<String, Object>) valueAsList.get(3);
            assertJsonNode(nodeFour, "3", users, null);

            Map<String, Object> nodeFive = (Map<String, Object>) valueAsList.get(4);
            Map<String, Object> expectedMapFive = Map.of("pippo", "pluto");
            assertJsonNode(nodeFive, "4", null, expectedMapFive);
        });
    }

    @Test
    public void testToJsonProperties() {
        testCall(
                db,
                "CREATE (a:Test {foo: 7}) RETURN apoc.convert.toJson(properties(a)) AS value",
                (row) -> assertMaps(Map.of("foo", 7L), Util.readMap((String) row.get("value"))));
    }

    @Test
    public void testToJsonMapOfNodes() {
        testCall(
                db,
                "CREATE (a:Test {foo: 7}), (b:Test {bar: 9}) RETURN apoc.convert.toJson({one: a, two: b}) AS value",
                (row) -> {
                    Map<String, Object> map = Util.fromJson((String) row.get("value"), Map.class);
                    List<String> test = List.of("Test");
                    assertEquals(2, map.size());
                    assertJsonNode((Map<String, Object>) map.get("one"), "0", test, Map.of("foo", 7L));
                    assertJsonNode((Map<String, Object>) map.get("two"), "1", test, Map.of("bar", 9L));
                });
    }

    @Test
    public void testToJsonRel() {
        testCall(
                db,
                "CREATE (f:User {name:'Adam'})-[rel:KNOWS {since: 1993.1, bffSince: duration('P5M1.5D')}]->(b:User {name:'Jim',age:42}) RETURN apoc.convert.toJson(rel) as value",
                (row) -> {
                    Map<String, Object> map = Util.fromJson((String) row.get("value"), Map.class);
                    List<String> user = List.of("User");
                    assertJsonNode((Map<String, Object>) map.get("start"), "0", user, Map.of("name", "Adam"));
                    assertJsonNode((Map<String, Object>) map.get("end"), "1", user, Map.of("name", "Jim", "age", 42L));
                    assertJsonRel(map, "0", "KNOWS", Map.of("since", 1993.1D, "bffSince", "P5M1DT12H"), RELATIONSHIP);
                });
    }

    @Test
    public void testToJsonPath() {
        testCall(
                db,
                "CREATE p=(a:Test {foo: 7})-[:TEST]->(b:Baz {a:'b'})<-[:TEST_2 {aa:'bb'}]-(:Bar {one:'www', two:2, three: localdatetime('2020-01-01')}) RETURN apoc.convert.toJson(p) AS value",
                (row) -> {
                    List<String> test = List.of("Test");
                    List<String> bar = List.of("Bar");
                    List<String> baz = List.of("Baz");
                    List<Object> list = Util.fromJson((String) row.get("value"), List.class);
                    assertEquals(5, list.size());

                    assertJsonNode((Map<String, Object>) list.get(0), "0", test, Map.of("foo", 7L));

                    Map<String, Object> firstRel = (Map<String, Object>) list.get(1);
                    assertJsonNode((Map<String, Object>) firstRel.get("start"), "0", test, Map.of("foo", 7L));
                    assertJsonNode((Map<String, Object>) firstRel.get("end"), "1", baz, Map.of("a", "b"));
                    assertJsonRel(firstRel, "0", "TEST", null, RELATIONSHIP);

                    assertJsonNode((Map<String, Object>) list.get(2), "1", baz, Map.of("a", "b"));

                    Map<String, Object> secondRel = (Map<String, Object>) list.get(3);
                    assertJsonNode(
                            (Map<String, Object>) secondRel.get("start"),
                            "2",
                            bar,
                            Map.of("one", "www", "two", 2L, "three", "2020-01-01T00:00"));
                    assertJsonNode((Map<String, Object>) secondRel.get("end"), "1", baz, Map.of("a", "b"));
                    assertJsonRel(secondRel, "1", "TEST_2", Map.of("aa", "bb"), RELATIONSHIP);

                    assertJsonNode(
                            (Map<String, Object>) list.get(4),
                            "2",
                            bar,
                            Map.of("one", "www", "two", 2L, "three", "2020-01-01T00:00"));
                });
    }

    @Test
    public void testToJsonListOfPath() {
        testCall(
                db,
                """
                          CREATE p=(a:Test {foo: 7})-[:TEST]->(b:Baa:Baz {a:'b'}), q=(:Omega {alpha: 'beta'})<-[:TEST_2 {aa:'bb'}]-(:Bar {one:'www'})
                          WITH collect(p) AS collectP, q RETURN apoc.convert.toJson(collectP+q) AS value""",
                (row) -> {
                    List<String> test = List.of("Test");
                    List<String> bar = List.of("Bar");
                    List<String> bazBaa = List.of("Baa", "Baz");
                    List<String> omega = List.of("Omega");
                    List<Object> list = Util.fromJson((String) row.get("value"), List.class);
                    assertEquals(2, list.size());
                    List<Object> firstSubList = (List<Object>) list.get(0);
                    List<Object> secondSubList = (List<Object>) list.get(1);

                    assertEquals(3, firstSubList.size());
                    assertJsonNode((Map<String, Object>) firstSubList.get(0), "0", test, Map.of("foo", 7L));
                    Map<String, Object> firstRel = (Map<String, Object>) firstSubList.get(1);
                    assertJsonNode((Map<String, Object>) firstRel.get("start"), "0", test, Map.of("foo", 7L));
                    assertJsonNode((Map<String, Object>) firstRel.get("end"), "1", bazBaa, Map.of("a", "b"));
                    assertJsonRel(firstRel, "0", "TEST", null, RELATIONSHIP);
                    assertJsonNode((Map<String, Object>) firstSubList.get(2), "1", bazBaa, Map.of("a", "b"));

                    assertEquals(3, secondSubList.size());
                    assertJsonNode((Map<String, Object>) secondSubList.get(0), "2", omega, Map.of("alpha", "beta"));
                    Map<String, Object> secondRel = (Map<String, Object>) secondSubList.get(1);
                    assertJsonNode((Map<String, Object>) secondRel.get("start"), "3", bar, Map.of("one", "www"));
                    assertJsonNode((Map<String, Object>) secondRel.get("end"), "2", omega, Map.of("alpha", "beta"));
                    assertJsonRel(secondRel, "1", "TEST_2", Map.of("aa", "bb"), RELATIONSHIP);
                    assertJsonNode((Map<String, Object>) secondSubList.get(2), "3", bar, Map.of("one", "www"));
                });
    }

    @Test
    public void testFromJsonList() {
        testCall(
                db,
                "RETURN apoc.convert.fromJsonList('[1,2,3]') as value",
                (row) -> assertEquals(asList(1L, 2L, 3L), row.get("value")));
        testCall(
                db,
                "RETURN apoc.convert.fromJsonList('{\"foo\":[1,2,3]}','$.foo') as value",
                (row) -> assertEquals(asList(1L, 2L, 3L), row.get("value")));
    }

    @Test
    public void testFromJsonMap() {
        testCall(db, "RETURN apoc.convert.fromJsonMap('{\"a\":42,\"b\":\"foo\",\"c\":[1,2,3]}')  as value", (row) -> {
            Map value = (Map) row.get("value");
            assertEquals(42L, value.get("a"));
            assertEquals("foo", value.get("b"));
            assertEquals(asList(1L, 2L, 3L), value.get("c"));
        });
    }

    @Test
    public void testSetJsonProperty() {
        testCall(
                db,
                "CREATE (n) WITH n CALL apoc.convert.setJsonProperty(n, 'json', [1,2,3]) RETURN n",
                (row) -> assertEquals("[1,2,3]", ((Node) row.get("n")).getProperty("json")));
    }

    @Test
    public void testGetJsonProperty() {
        testCall(
                db,
                "CREATE (n {json:'[1,2,3]'}) RETURN apoc.convert.getJsonProperty(n, 'json') AS value",
                (row) -> assertEquals(asList(1L, 2L, 3L), row.get("value")));
        testCall(
                db,
                "CREATE (n {json:'{\"foo\":[1,2,3]}'}) RETURN apoc.convert.getJsonProperty(n, 'json','$.foo') AS value",
                (row) -> assertEquals(asList(1L, 2L, 3L), row.get("value")));
    }

    @Test
    public void testGetJsonPropertyMap() {
        testCall(
                db,
                "CREATE (n {json:'{a:[1,2,3]}'}) RETURN apoc.convert.getJsonProperty(n, 'json') as value",
                (row) -> assertEquals(map("a", asList(1L, 2L, 3L)), row.get("value")));
        testCall(
                db,
                "CREATE (n {json:'{a:[1,2,3]}'}) RETURN apoc.convert.getJsonProperty(n, 'json','$.a') as value",
                (row) -> assertEquals(asList(1L, 2L, 3L), row.get("value")));
    }

    @Test
    public void testToTreeIssue1685() {
        String movies = Util.readResourceFile("movies.cypher");
        db.executeTransactionally(movies);

        testCall(
                db,
                """
                        MATCH path = (k:Person {name:'Keanu Reeves'})-[*..5]-(x)
                        WITH collect(path) AS paths
                        CALL apoc.convert.toTree(paths)
                        YIELD value
                        RETURN value""",
                (row) -> {
                    Map<?, ?> root = (Map<?, ?>) row.get("value");
                    assertEquals("Person", root.get("_type"));
                    List<Object> actedInList = (List<Object>) root.get("acted_in");
                    assertEquals(7, actedInList.size());
                    List<Object> innerList = (List) ((Map<String, Object>) actedInList.get(1)).get("acted_in");
                    assertEquals(9, ((Map<String, Object>) innerList.get(0)).size());
                });
    }

    @Test
    public void testToTreeIssue2190() {
        db.executeTransactionally("""
                CREATE (root:TreeNode {name:'root'})
                CREATE (c0:TreeNode {name: 'c0'})
                CREATE (c1:TreeNode {name: 'c1'})
                CREATE (c2:TreeNode {name: 'c2'})
                CREATE (c00:TreeNode {name : 'c00'})
                CREATE (c01:TreeNode {name : 'c01'})
                CREATE (c10:TreeNode {name : 'c10'})
                CREATE (c100:TreeNode {name : 'c100'})
                CREATE (root)-[:CHILD {order: 0}]->(c0)
                CREATE (root)-[:CHILD {order: 1}]->(c1)
                CREATE (root)-[:CHILD { order: 2}]->(c2)
                CREATE (c0)-[:CHILD {order: 0}]->(c00)
                CREATE (c0)-[:CHILD {order: 1}]->(c01)
                CREATE (c1)-[:CHILD {order: 0}]->(c10)
                CREATE (c10)-[:CHILD {order: 0}]->(c100)""");

        testCall(
                db,
                """
                        MATCH(root:TreeNode) WHERE root.name = "root"
                        MATCH path = (root)-[cl:CHILD*]->(c:TreeNode)
                        WITH path, [r IN relationships(path) | r.order] AS orders
                        ORDER BY orders
                        WITH COLLECT(path) AS paths
                        CALL apoc.convert.toTree(paths, true, {sortPaths: false}) YIELD value AS tree
                        RETURN tree""",
                (row) -> {
                    Map tree = (Map) row.get("tree");
                    final List<Map> child = (List<Map>) tree.get("child");
                    final Object firstChildName = child.get(0).get("name");
                    assertEquals("c0", firstChildName);
                });

        db.executeTransactionally("MATCH (n:TreeNode) DETACH DELETE n");
    }

    @Test
    public void testToTree() {
        testCall(
                db,
                "CREATE p1=(m:Movie {title:'M'})<-[:ACTED_IN {role:'R1'}]-(:Actor {name:'A1'}), "
                        + " p2 = (m)<-[:ACTED_IN  {role:'R2'}]-(:Actor {name:'A2'}) WITH [p1,p2] as paths "
                        + " CALL apoc.convert.toTree(paths) YIELD value RETURN value",
                (row) -> {
                    Map root = (Map) row.get("value");
                    assertEquals("Movie", root.get("_type"));
                    assertEquals("M", root.get("title"));
                    List<Map> actors = (List<Map>) root.get("acted_in");
                    assertEquals("Actor", actors.get(0).get("_type"));
                    assertEquals(true, actors.get(0).get("name").toString().matches("A[12]"));
                    assertEquals(
                            true, actors.get(0).get("acted_in.role").toString().matches("R[12]"));
                });
    }

    @Test
    public void testToTreeUpperCaseRels() {
        testCall(
                db,
                "CREATE p1=(m:Movie {title:'M'})<-[:ACTED_IN {role:'R1'}]-(:Actor {name:'A1'}), "
                        + " p2 = (m)<-[:ACTED_IN  {role:'R2'}]-(:Actor {name:'A2'}) WITH [p1,p2] as paths "
                        + " CALL apoc.convert.toTree(paths,false) YIELD value RETURN value",
                (row) -> {
                    Map root = (Map) row.get("value");
                    assertEquals("Movie", root.get("_type"));
                    assertEquals("M", root.get("title"));
                    List<Map> actors = (List<Map>) root.get("ACTED_IN");
                    assertEquals("Actor", actors.get(0).get("_type"));
                    assertEquals(true, actors.get(0).get("name").toString().matches("A[12]"));
                    assertEquals(
                            true, actors.get(0).get("ACTED_IN.role").toString().matches("R[12]"));
                });
    }

    @Test
    public void testTreeOfEmptyList() {
        testCall(db, " CALL apoc.convert.toTree([]) YIELD value RETURN value", (row) -> {
            Map root = (Map) row.get("value");
            assertTrue(root.isEmpty());
        });
    }

    @Test
    public void testToTreeLeafNodes() {
        String createStatement = """
                CREATE
                (c1:Category {name: 'PC'}),
                (c1)-[:subcategory {id:1}]->(c2:Category {name: 'Parts'}),
                (c2)-[:subcategory {id:2}]->(c3:Category {name: 'CPU'})""";
        db.executeTransactionally(createStatement);

        String call = """
                MATCH p=(n:Category)-[:subcategory*]->(m)
                WHERE NOT (m)-[:subcategory]->() AND NOT ()-[:subcategory]->(n)
                WITH COLLECT(p) AS ps
                CALL apoc.convert.toTree(ps) yield value
                RETURN value;""";
        testCall(db, call, (row) -> {
            Map root = (Map) row.get("value");

            assertEquals("Category", root.get("_type"));
            assertEquals("PC", root.get("name"));
            List<Map> parts = (List<Map>) root.get("subcategory");
            assertEquals(1, parts.size());
            Map pcParts = parts.get(0);
            assertEquals("Category", pcParts.get("_type"));
            assertEquals("Parts", pcParts.get("name"));
            List<Map> subParts = (List<Map>) pcParts.get("subcategory");
            Map cpu = subParts.get(0);
            assertEquals(1, subParts.size());
            assertEquals("Category", cpu.get("_type"));
            assertEquals("CPU", cpu.get("name"));
        });
    }

    @Test
    public void testToJsonMapSortingProperties() {
        testCall(
                db,
                "WITH {b:8, d:3, a:2, E: 12, C:9} as map RETURN apoc.convert.toSortedJsonMap(map, false) as value",
                (row) -> assertEquals("{\"C\":9,\"E\":12,\"a\":2,\"b\":8,\"d\":3}", row.get("value")));
    }

    @Test
    public void testToJsonMapSortingPropertiesIgnoringCase() {
        testCall(
                db,
                "WITH {b:8, d:3, a:2, E: 12, C:9} as map RETURN apoc.convert.toSortedJsonMap(map) as value",
                (row) -> assertEquals("{\"a\":2,\"b\":8,\"C\":9,\"d\":3,\"E\":12}", row.get("value")));
    }

    @Test
    public void testToTreeParentNodes() {
        String createDatabase =
                """
                        CREATE (b:Bib {id: '57523a6f-fda9-4a61-c4f6-08d47cdcf0cd', langId: 2})-[:HAS {id: "rel1"}]->(c:Comm {id: 'a34fd608-1751-0b5d-cb38-6991297fa9c9', langId: 2}),
                        (b)-[:HAS {id: "rel2"}]->(c1:Comm {id: 'a34fd608-262b-678a-cb38-6991297fa9c8', langId: 2}),
                        (u:User {id: 'facebook|680594762097202'})-[:Flag  {id: "rel3", Created: '2018-11-21T11:22:01', FlagType: 4}]->(c1),
                        (u)-[:Flag {id: "rel4", Created: '2018-11-21T11:22:04', FlagType: 5}]->(c),
                        (u1:User {id: 'google-oauth2|106707535753175966005'})-[:Flag {id: "rel5", Created: '2018-11-21T11:20:34', FlagType: 2}]->(c),
                        (u1)-[:Flag {id: "rel6", Created: '2018-11-21T11:20:31', FlagType: 1}]->(c1)""";
        db.executeTransactionally(createDatabase);

        String queryToGetGeneratedIds = """
                MATCH (n)
                RETURN n.id as givenId, id(n) as id, elementId(n) as elementId
                UNION
                MATCH ()-[r]->()
                RETURN r.id as givenId, id(r) as id, elementId(r) as elementId
                """;

        db.executeTransactionally(queryToGetGeneratedIds);
        Map<String, Map<String, String>> generatedIdsMapping = db.executeTransactionally(
                queryToGetGeneratedIds,
                Collections.emptyMap(),
                result -> result.stream()
                        .collect(
                                Collectors.toMap(
                                        row -> row.get("givenId").toString(),
                                        row -> Map.of(
                                                "id", row.get("id").toString(),
                                                "elementId", row.get("elementId").toString()
                                        )
                                )
                        ));

        String call = """
                MATCH (parent:Bib {id: '57523a6f-fda9-4a61-c4f6-08d47cdcf0cd'})
                WITH parent
                OPTIONAL MATCH childFlagPath=(parent)-[:HAS]->(:Comm)<-[:Flag]-(:User)
                WITH COLLECT(childFlagPath) AS cfp
                CALL apoc.convert.toTree(cfp) yield value
                RETURN value""";

        testCall(db, call, (row) -> {
            Map<?, ?> root = (Map<?, ?>) row.get("value");

            assertEquals("Bib", root.get("_type"));
            assertEquals(generatedIdsMapping.get("57523a6f-fda9-4a61-c4f6-08d47cdcf0cd").get("id"), root.get("_id").toString());
            assertEquals(generatedIdsMapping.get("57523a6f-fda9-4a61-c4f6-08d47cdcf0cd").get("elementId"), root.get("_elementId").toString());
            assertEquals("57523a6f-fda9-4a61-c4f6-08d47cdcf0cd", root.get("id"));
            assertEquals(2L, root.get("langId"));

            List<Map> has = (List<Map>) root.get("has"); // HAS REL
            assertEquals(2, has.size());

            Map<?, ?> hasPart = has.get(0);

            assertEquals("Comm", hasPart.get("_type"));
            assertEquals(generatedIdsMapping.get("a34fd608-262b-678a-cb38-6991297fa9c8").get("id"), hasPart.get("_id").toString());
            assertEquals(generatedIdsMapping.get("a34fd608-262b-678a-cb38-6991297fa9c8").get("elementId"), hasPart.get("_elementId").toString());
            assertEquals("a34fd608-262b-678a-cb38-6991297fa9c8", hasPart.get("id"));
            assertEquals(2L, hasPart.get("langId"));
            List<Map> subParts = (List<Map>) hasPart.get("flag");
            assertEquals(2, subParts.size());

            /*USER*/
            MatcherAssert.assertThat(
                    subParts,
                    Matchers.hasItem(MapUtil.map(
                            "_type", "User",
                            "flag.Created", "2018-11-21T11:22:01",
                            "_id", Long.parseLong(generatedIdsMapping.get("facebook|680594762097202").get("id")),
                            "_elementId", generatedIdsMapping.get("facebook|680594762097202").get("elementId"),
                            "id", "facebook|680594762097202",
                            "flag.id", "rel3",
                            "flag._id", Long.parseLong(generatedIdsMapping.get("rel3").get("id")),
                            "flag._elementId", generatedIdsMapping.get("rel3").get("elementId"),
                            "flag.id", "rel3",
                            "flag.FlagType", 4L)));

            MatcherAssert.assertThat(
                    subParts,
                    Matchers.hasItem(MapUtil.map(
                            "_type", "User",
                            "flag.Created", "2018-11-21T11:20:31",
                            "_id", Long.parseLong(generatedIdsMapping.get("google-oauth2|106707535753175966005").get("id")),
                            "_elementId", generatedIdsMapping.get("google-oauth2|106707535753175966005").get("elementId"),
                            "id", "google-oauth2|106707535753175966005",
                            "flag.id", "rel6",
                            "flag._id", Long.parseLong(generatedIdsMapping.get("rel6").get("id")),
                            "flag._elementId", generatedIdsMapping.get("rel6").get("elementId"),
                            "flag.FlagType", 1L)));
            hasPart = has.get(1);

            assertEquals("Comm", hasPart.get("_type"));
            assertEquals(Long.parseLong(generatedIdsMapping.get("a34fd608-1751-0b5d-cb38-6991297fa9c9").get("id")), hasPart.get("_id"));
            assertEquals("a34fd608-1751-0b5d-cb38-6991297fa9c9", hasPart.get("id"));
            assertEquals(2L, hasPart.get("langId"));
        });
    }

    @Test
    public void testToTreeLeafNodesWithConfigInclude() {
        statementForConfig(db);
        String call = """
                MATCH p=(n:Category)-[:subcategory*]->(m)
                WHERE NOT (m)-[:subcategory]->() AND NOT ()-[:subcategory]->(n)
                WITH COLLECT(p) AS ps
                CALL apoc.convert.toTree(ps, true, {nodes: {Category: ['name']}, rels: {subcategory:['id']}}) yield value
                RETURN value;""";
        testCall(db, call, (row) -> {
            Map root = (Map) row.get("value");
            assertEquals("Category", root.get("_type"));
            assertEquals("PC", root.get("name"));
            assertNull(root.get("surname"));
            List<Map> parts = (List<Map>) root.get("subcategory");
            assertEquals(1, parts.size());
            Map pcParts = parts.get(0);
            assertEquals("Category", pcParts.get("_type"));
            assertEquals("Parts", pcParts.get("name"));
            assertEquals(1L, pcParts.get("subcategory.id"));
            assertNull(pcParts.get("subcategory.subCat"));
            List<Map> subParts = (List<Map>) pcParts.get("subcategory");
            Map cpu = subParts.get(0);
            assertEquals("Category", pcParts.get("_type"));
            assertEquals("CPU", cpu.get("name"));
            assertEquals(2L, cpu.get("subcategory.id"));
            assertNull(cpu.get("subcategory.subCat"));
        });
    }

    @Test
    public void testToTreeLeafNodesWithConfigExclude() {
        statementForConfig(db);
        String call = """
                MATCH p=(n:Category)-[:subcategory*]->(m)
                WHERE NOT (m)-[:subcategory]->() AND NOT ()-[:subcategory]->(n)
                WITH COLLECT(p) AS ps
                CALL apoc.convert.toTree(ps, true,{nodes: {Category: ['-name']}, rels: {subcategory:['-id']}}) yield value
                RETURN value;""";
        testCall(db, call, (row) -> {
            Map root = (Map) row.get("value");
            assertEquals("Category", root.get("_type"));
            assertFalse("Should not contain key `name`", root.containsKey("name"));
            assertEquals("computer", root.get("surname"));
            List<Map> parts = (List<Map>) root.get("subcategory");
            assertEquals(1, parts.size());
            Map pcParts = parts.get(0);
            assertEquals("Category", pcParts.get("_type"));
            assertFalse("Should not contain key `name`", pcParts.containsKey("name"));
            assertFalse("Should not contain key `subcategory.id`", pcParts.containsKey("subcategory.id"));
            assertEquals("gen", pcParts.get("subcategory.subCat"));
            List<Map> subParts = (List<Map>) pcParts.get("subcategory");
            Map cpu = subParts.get(0);
            assertEquals("Category", pcParts.get("_type"));
            assertFalse("Should not contain key `name`", cpu.containsKey("name"));
            assertFalse("Should not contain key `subcategory.id`", cpu.containsKey("subcategory.id"));
            assertEquals("ex", cpu.get("subcategory.subCat"));
        });
    }

    @Test
    public void testToTreeLeafNodesWithConfigExcludeInclude() {
        statementForConfig(db);
        String call = """
                MATCH p=(n:Category)-[:subcategory*]->(m)
                WHERE NOT (m)-[:subcategory]->() AND NOT ()-[:subcategory]->(n)
                WITH COLLECT(p) AS ps
                CALL apoc.convert.toTree(ps, true, {nodes: {Category: ['name']}, rels: {subcategory:['-id']}}) yield value
                RETURN value;""";
        testCall(db, call, (row) -> {
            Map root = (Map) row.get("value");
            assertEquals("Category", root.get("_type"));
            assertEquals("PC", root.get("name"));
            assertFalse("Should not contain key `surname`", root.containsKey("surname"));
            List<Map> parts = (List<Map>) root.get("subcategory");
            assertEquals(1, parts.size());
            Map pcParts = parts.get(0);
            assertEquals("Category", pcParts.get("_type"));
            assertEquals("Parts", pcParts.get("name"));
            assertFalse("Should not contain key `subcategory.id`", pcParts.containsKey("subcategory.id"));
            assertEquals("gen", pcParts.get("subcategory.subCat"));
            List<Map> subParts = (List<Map>) pcParts.get("subcategory");
            Map cpu = subParts.get(0);
            assertEquals("Category", pcParts.get("_type"));
            assertEquals("CPU", cpu.get("name"));
            assertFalse("Should not contain key `subcategory.id`", cpu.containsKey("subcategory.id"));
            assertEquals("ex", cpu.get("subcategory.subCat"));
        });
    }

    @Test
    public void testToTreeLeafNodesWithConfigOnlyInclude() {
        statementForConfig(db);
        String call = """
                MATCH p=(n:Category)-[:subcategory*]->(m)
                WHERE NOT (m)-[:subcategory]->() AND NOT ()-[:subcategory]->(n)
                WITH COLLECT(p) AS ps
                CALL apoc.convert.toTree(ps, true, {nodes: {Category: ['name', 'surname']}}) yield value
                RETURN value;""";
        testCall(db, call, (row) -> {
            Map root = (Map) row.get("value");
            assertEquals("Category", root.get("_type"));
            assertEquals("PC", root.get("name"));
            assertEquals("computer", root.get("surname"));
            List<Map> parts = (List<Map>) root.get("subcategory");
            assertEquals(1, parts.size());
            Map pcParts = parts.get(0);
            assertEquals("Category", pcParts.get("_type"));
            assertEquals("Parts", pcParts.get("name"));
            assertEquals(1L, pcParts.get("subcategory.id"));
            assertEquals("gen", pcParts.get("subcategory.subCat"));
            List<Map> subParts = (List<Map>) pcParts.get("subcategory");
            Map cpu = subParts.get(0);
            assertEquals("Category", pcParts.get("_type"));
            assertEquals("CPU", cpu.get("name"));
            assertEquals(2L, cpu.get("subcategory.id"));
            assertEquals("ex", cpu.get("subcategory.subCat"));
        });
    }

    @Test
    public void testToTreeLeafNodesWithConfigErrorInclude() {
        statementForConfig(db);
        String call = """
                MATCH p=(n:Category)-[:subcategory*]->(m)
                WHERE NOT (m)-[:subcategory]->() AND NOT ()-[:subcategory]->(n)
                WITH COLLECT(p) AS ps
                CALL apoc.convert.toTree(ps, true, {nodes: {Category: ['-name','name']}, rels: {subcategory:['-id']}}) yield value
                RETURN value;""";
        try {
            testResult(db, call, Result::close);
        } catch (QueryExecutionException e) {
            Throwable except = ExceptionUtils.getRootCause(e);
            TestCase.assertTrue(except instanceof RuntimeException);
            assertEquals("Only include or exclude attribute are possible!", except.getMessage());
        }
    }

    @Test
    public void testToTreeDoesNotRemoveNonDuplicateRels() {
        String createStatement = """
            CREATE (v1:N {id: 'n21', name: 'Node 21', p2: 'node21'})
            CREATE (v2:N {id: 'n22', name: 'Node 22', p2: 'node22'})
            CREATE (v1)-[:R {prop1: 'n21->n22 [1]', prop2: 'Rel 1'}]->(v2)
            CREATE (v1)-[:R {prop1: 'n21->n22 [2]', prop2: 'Rel 2'}]->(v2)""";

        db.executeTransactionally(createStatement);

        String query = """
                MATCH p1 = (n:N {id:'n21'})-[e1]->(m1:N)
                WITH  COLLECT(p1) as paths
                CALL apoc.convert.toTree(paths, false)
                YIELD value
                RETURN value""";

        testResult(
                db,
                query,
                res -> {
                    Map<String, Object> value = (Map<String, Object>) res.next().get("value");
                    // Check Parent Node: (v1:T1 {id: 'v21', name: 'Vertex 21', p2: 'value21'})
                    assertEquals("n21", value.get("id"));
                    assertEquals("N", value.get("_type"));
                    assertEquals("node21", value.get("p2"));
                    assertEquals("Node 21", value.get("name"));
                    // Check Children Nodes
                    List<Map<String, Object>> RBranches = (List<Map<String, Object>>) value.get("R");
                    Map<String, Object> rel1 = Map.of("p2", "node22", "name", "Node 22", "id", "n22", "R.prop1", "n21->n22 [1]", "R.prop2", "Rel 1");
                    Map<String, Object> rel2 = Map.of("p2", "node22", "name", "Node 22", "id", "n22", "R.prop1", "n21->n22 [2]", "R.prop2", "Rel 2");
                    assertEquals(2, RBranches.size());
                    if (RBranches.get(0).entrySet().containsAll(rel1.entrySet())) {
                        assertTrue(RBranches.get(1).entrySet().containsAll(rel2.entrySet()));
                    } else {
                        assertTrue(RBranches.get(0).entrySet().containsAll(rel1.entrySet()));
                        assertTrue(RBranches.get(1).entrySet().containsAll(rel2.entrySet()));
                    }
                    assertFalse(res.hasNext());
                });
    }

    @Test
    public void testToTreeLeafNodesWithConfigErrorExclude() {
        statementForConfig(db);
        String call = """
                MATCH p=(n:Category)-[:subcategory*]->(m)
                WHERE NOT (m)-[:subcategory]->() AND NOT ()-[:subcategory]->(n)
                WITH COLLECT(p) AS ps
                CALL apoc.convert.toTree(ps, true, {nodes: {Category: ['-name']}, rels: {subcategory:['-id','name']}}) yield value
                RETURN value;""";
        try {
            testResult(db, call, Result::close);
        } catch (QueryExecutionException e) {
            Throwable except = ExceptionUtils.getRootCause(e);
            TestCase.assertTrue(except instanceof RuntimeException);
            assertEquals("Only include or exclude attribute are possible!", except.getMessage());
        }
    }

    private static void statementForConfig(GraphDatabaseService db) {
        String createStatement = """
                CREATE
                (c1:Category {name: 'PC', surname: 'computer'}),
                (c1)-[:subcategory {id:1, subCat: 'gen'}]->(c2:Category {name: 'Parts'}),
                (c2)-[:subcategory {id:2, subCat: 'ex'}]->(c3:Category {name: 'CPU'})""";

        db.executeTransactionally(createStatement);
    }

    public static void assertMaps(Map<String, Object> expected, Map<String, Object> actual) {
        if (expected == null) {
            assertNull(actual);
        } else {
            actual.entrySet().forEach(i -> {
                if (i.getValue() instanceof Map) {
                    assertMaps((Map<String, Object>) expected.get(i.getKey()), (Map<String, Object>) i.getValue());
                } else {
                    assertEquals(expected.get(i.getKey()), i.getValue());
                }
            });
            assertEquals(expected.keySet(), actual.keySet());
        }
    }

    public static void assertJsonNode(
            Map<String, Object> node, String id, List<String> labels, Map<String, Object> properties) {
        assertEquals(id, node.get("id"));
        assertEquals(labels, node.get("labels"));
        assertMaps(properties, (Map<String, Object>) node.get("properties"));
        assertEquals(NODE, node.get("type"));
    }

    public static void assertJsonRel(
            Map<String, Object> rel, String id, String label, Map<String, Object> properties, String type) {
        assertEquals(id, rel.get("id"));
        assertEquals(label, rel.get("label"));
        assertMaps(properties, (Map<String, Object>) rel.get("properties"));
        assertEquals(type, rel.get("type"));
    }
}
