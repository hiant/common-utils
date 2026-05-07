package io.github.hiant.common.utils;

import org.junit.After;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for annotation-based desensitization utilities.
 * <p>
 * Tests {@link Desensitize} annotation, {@link DesensitizeType} enum,
 * and {@link ToStringDesensitizeUtils} functionality.
 */
public class ToStringDesensitizeUtilsTest {

    /**
     * Sample user entity with various desensitization annotations.
     */
    static class UserInfo {
        private String name;

        @Desensitize(type = DesensitizeType.MOBILE_PHONE)
        private String phone;

        @Desensitize(type = DesensitizeType.ID_CARD)
        private String idCard;

        @Desensitize(type = DesensitizeType.EMAIL)
        private String email;

        @Desensitize(keepPrefix = 2, keepSuffix = 2)
        private String address;

        @Desensitize(type = DesensitizeType.NAME)
        private String realName;

        public UserInfo(String name, String phone, String idCard, String email, String address, String realName) {
            this.name = name;
            this.phone = phone;
            this.idCard = idCard;
            this.email = email;
            this.address = address;
            this.realName = realName;
        }
    }

    static class PlainInfo {
        private String field1;
        private String field2;

        public PlainInfo(String field1, String field2) {
            this.field1 = field1;
            this.field2 = field2;
        }
    }

    static class PrefixOnlyInfo {
        @Desensitize(type = DesensitizeType.PREFIX_ONLY)
        private String code;

        @Desensitize(type = DesensitizeType.PREFIX_ONLY, keepPrefix = 3, keepSuffix = 2)
        private String customCode;

        public PrefixOnlyInfo(String code, String customCode) {
            this.code = code;
            this.customCode = customCode;
        }
    }

    static class ContactGroup {
        @Desensitize(type = DesensitizeType.MOBILE_PHONE)
        private List<String>        phones;

        @Desensitize(type = DesensitizeType.MOBILE_PHONE)
        private String[]            phoneArray;

        @Desensitize(type = DesensitizeType.EMAIL)
        private Map<String, String> emails;

        public ContactGroup(List<String> phones, String[] phoneArray, Map<String, String> emails) {
            this.phones = phones;
            this.phoneArray = phoneArray;
            this.emails = emails;
        }
    }

    static class Team {
        private UserInfo       owner;
        private List<UserInfo> members;

        public Team(UserInfo owner, List<UserInfo> members) {
            this.owner = owner;
            this.members = members;
        }
    }

    static class Node {
        private String            name;
        private Node              next;
        private List<Node>        children;
        private Map<String, Node> links;

        public Node(String name) {
            this.name = name;
        }
    }

    @Test
    public void testToDesensitizeString_nullObject_returnsNull() {
        String result = ToStringDesensitizeUtils.toDesensitizeString(null);
        assertEquals("null", result);
    }

    @Test
    public void testToDesensitizeString_mobilePhone_desensitized() {
        UserInfo user = new UserInfo("test", "13812345678", null, null, null, null);
        String result = ToStringDesensitizeUtils.toDesensitizeString(user);

        assertTrue("Should contain desensitized phone", result.contains("phone=138****5678"));
    }

    @Test
    public void testToDesensitizeString_idCard_desensitized() {
        UserInfo user = new UserInfo("test", null, "110101199001011234", null, null, null);
        String result = ToStringDesensitizeUtils.toDesensitizeString(user);

        assertTrue("Should contain desensitized ID card", result.contains("idCard=110101****1234"));
    }

    @Test
    public void testToDesensitizeString_email_desensitized() {
        UserInfo user = new UserInfo("test", null, null, "john.doe@example.com", null, null);
        String result = ToStringDesensitizeUtils.toDesensitizeString(user);

        assertTrue("Should contain desensitized email", result.contains("email=j****@example.com"));
    }

    @Test
    public void testToDesensitizeString_name_desensitized() {
        UserInfo user = new UserInfo("test", null, null, null, null, "John Doe");
        String result = ToStringDesensitizeUtils.toDesensitizeString(user);

        assertTrue("Should contain desensitized name", result.contains("realName=J****"));
    }

    @Test
    public void testToDesensitizeString_customPrefixSuffix_desensitized() {
        UserInfo user = new UserInfo("test", null, null, null, "Beijing Haidian District", null);
        String result = ToStringDesensitizeUtils.toDesensitizeString(user);

        assertTrue("Should contain desensitized address with prefix 2 and suffix 2",
            result.contains("address=Be****ct"));
    }

    @Test
    public void testToDesensitizeString_noAnnotation_unchanged() {
        PlainInfo plain = new PlainInfo("value1", "value2");
        String result = ToStringDesensitizeUtils.toDesensitizeString(plain);

        assertTrue("Should contain unchanged field1", result.contains("field1=value1"));
        assertTrue("Should contain unchanged field2", result.contains("field2=value2"));
    }

    @Test
    public void testToDesensitizeString_nullFieldValue_unchanged() {
        UserInfo user = new UserInfo("test", null, null, null, null, null);
        String result = ToStringDesensitizeUtils.toDesensitizeString(user);

        assertTrue("Should contain null phone", result.contains("phone=null"));
    }

    @Test
    public void testToDesensitizeString_emptyFieldValue_unchanged() {
        UserInfo user = new UserInfo("test", "", null, null, null, null);
        String result = ToStringDesensitizeUtils.toDesensitizeString(user);

        assertTrue("Should contain empty phone", result.contains("phone="));
    }

    @Test
    public void testToDesensitizeString_nestedPojo_recursesAndDesensitizesNestedFields() {
        Team team = new Team(
            new UserInfo("owner", "13812345678", null, null, null, null),
            Collections.singletonList(new UserInfo("member", "13987654321", null, null, null, null)));

        String result = ToStringDesensitizeUtils.toDesensitizeString(team);

        assertTrue(result.contains("owner=UserInfo("));
        assertTrue(result.contains("phone=138****5678"));
        assertTrue(result.contains("members=[UserInfo("));
        assertTrue(result.contains("phone=139****4321"));
    }

    @Test
    public void testToDesensitizeString_annotatedList_masksStringElements() {
        ContactGroup group = new ContactGroup(
            Arrays.asList("13812345678", null, ""),
            null,
            null);

        String result = ToStringDesensitizeUtils.toDesensitizeString(group);

        assertTrue(result.contains("phones=[138****5678, null, ]"));
    }

    @Test
    public void testToDesensitizeString_annotatedArray_masksStringElements() {
        ContactGroup group = new ContactGroup(
            null,
            new String[] { "13812345678", "13987654321" },
            null);

        String result = ToStringDesensitizeUtils.toDesensitizeString(group);

        assertTrue(result.contains("phoneArray=[138****5678, 139****4321]"));
    }

    @Test
    public void testToDesensitizeString_annotatedMap_masksValuesOnly() {
        Map<String, String> emails = new LinkedHashMap<String, String>();
        emails.put("owner", "john.doe@example.com");
        emails.put("admin", "a@example.com");
        ContactGroup group = new ContactGroup(null, null, emails);

        String result = ToStringDesensitizeUtils.toDesensitizeString(group);

        assertTrue(result.contains("emails={owner=j****@example.com, admin=a****@example.com}"));
    }

    @Test
    public void testToDesensitizeString_selfReference_rendersCycleMarker() {
        Node node = new Node("root");
        node.next = node;

        String result = ToStringDesensitizeUtils.toDesensitizeString(node);

        assertTrue(result.contains("next=<cycle:Node>"));
    }

    @Test
    public void testToDesensitizeString_cycleThroughCollectionAndMap_rendersCycleMarker() {
        Node root = new Node("root");
        root.children = Collections.singletonList(root);
        root.links = new LinkedHashMap<>();
        root.links.put("self", root);

        String result = ToStringDesensitizeUtils.toDesensitizeString(root);

        assertTrue(result.contains("children=[<cycle:Node>]"));
        assertTrue(result.contains("links={self=<cycle:Node>}"));
    }

    @Test
    public void testDesensitizeType_default_hasNoPreset() {
        assertFalse("DEFAULT should have no prefix preset", DesensitizeType.DEFAULT.getDefaultPrefix().isPresent());
        assertFalse("DEFAULT should have no suffix preset", DesensitizeType.DEFAULT.getDefaultSuffix().isPresent());
    }

    @Test
    public void testDesensitizeType_mobilePhone_hasCorrectPreset() {
        assertEquals(3, DesensitizeType.MOBILE_PHONE.getDefaultPrefix().orElse(-1));
        assertEquals(4, DesensitizeType.MOBILE_PHONE.getDefaultSuffix().orElse(-1));
    }

    @Test
    public void testDesensitizeType_idCard_hasCorrectPreset() {
        assertEquals(6, DesensitizeType.ID_CARD.getDefaultPrefix().orElse(-1));
        assertEquals(4, DesensitizeType.ID_CARD.getDefaultSuffix().orElse(-1));
    }

    @Test
    public void testDesensitizeType_name_hasCorrectPreset() {
        assertEquals(1, DesensitizeType.NAME.getDefaultPrefix().orElse(-1));
        assertEquals(0, DesensitizeType.NAME.getDefaultSuffix().orElse(-1));
    }

    @Test
    public void testDesensitizeType_prefixOnly_hasCorrectPreset() {
        assertEquals(1, DesensitizeType.PREFIX_ONLY.getDefaultPrefix().orElse(-1));
        assertEquals(0, DesensitizeType.PREFIX_ONLY.getDefaultSuffix().orElse(-1));
    }

    @Test
    public void testDesensitizeType_email_hasNoPreset() {
        assertFalse("EMAIL should have no prefix preset", DesensitizeType.EMAIL.getDefaultPrefix().isPresent());
        assertFalse("EMAIL should have no suffix preset", DesensitizeType.EMAIL.getDefaultSuffix().isPresent());
    }

    @Test
    public void testDesensitizeEmail_normalEmail_desensitized() {
        String result = DesensitizationUtils.desensitizeEmail("john.doe@example.com");
        assertEquals("j****@example.com", result);
    }

    @Test
    public void testDesensitizeEmail_shortLocalPart_desensitized() {
        String result = DesensitizationUtils.desensitizeEmail("a@example.com");
        assertEquals("a****@example.com", result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDesensitizeEmail_nullEmail_throwsException() {
        DesensitizationUtils.desensitizeEmail(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDesensitizeEmail_invalidEmail_throwsException() {
        DesensitizationUtils.desensitizeEmail("not-an-email");
    }

    @Test
    public void testToDesensitizeString_prefixOnlyType_appliesDefaultAndCustomPrefix() {
        PrefixOnlyInfo info = new PrefixOnlyInfo("abcdef", "abcdef");

        String result = ToStringDesensitizeUtils.toDesensitizeString(info);

        assertTrue(result.contains("code=a****"));
        assertTrue(result.contains("customCode=abc****"));
    }

    static class BaseEntity implements Desensitizable {
        private Long   id;
        private String createTime;

        public BaseEntity(Long id, String createTime) {
            this.id = id;
            this.createTime = createTime;
        }

        @Override
        public String toString() {
            return toDesensitizedString();
        }
    }

    static class Employee extends BaseEntity {
        private String name;

        @Desensitize(type = DesensitizeType.MOBILE_PHONE)
        private String phone;

        @Desensitize(type = DesensitizeType.ID_CARD)
        private String idCard;

        public Employee(Long id, String createTime, String name, String phone, String idCard) {
            super(id, createTime);
            this.name = name;
            this.phone = phone;
            this.idCard = idCard;
        }
    }

    static class Manager extends Employee {
        private String department;

        @Desensitize(type = DesensitizeType.EMAIL)
        private String workEmail;

        public Manager(Long id,
                       String createTime,
                       String name,
                       String phone,
                       String idCard,
                       String department,
                       String workEmail) {
            super(id, createTime, name, phone, idCard);
            this.department = department;
            this.workEmail = workEmail;
        }
    }

    @Test
    public void testInheritance_childClassIncludesParentFields() {
        Employee emp = new Employee(1L, "2024-01-01", "John", "13812345678", "110101199001011234");
        String result = emp.toString();

        assertTrue("Should contain id from parent", result.contains("id=1"));
        assertTrue("Should contain createTime from parent", result.contains("createTime=2024-01-01"));
        assertTrue("Should contain name", result.contains("name=John"));
        assertTrue("Should contain desensitized phone", result.contains("phone=138****5678"));
        assertTrue("Should contain desensitized idCard", result.contains("idCard=110101****1234"));
    }

    @Test
    public void testInheritance_multiLevelHierarchy() {
        Manager mgr = new Manager(2L, "2024-02-01", "Jane", "13987654321", "320101198505051234",
            "Engineering", "jane.doe@company.com");
        String result = mgr.toString();

        assertTrue("Should contain id from grandparent", result.contains("id=2"));
        assertTrue("Should contain createTime from grandparent", result.contains("createTime=2024-02-01"));
        assertTrue("Should contain name from parent", result.contains("name=Jane"));
        assertTrue("Should contain desensitized phone from parent", result.contains("phone=139****4321"));
        assertTrue("Should contain department from child", result.contains("department=Engineering"));
        assertTrue("Should contain desensitized email from child", result.contains("workEmail=j****@company.com"));
    }

    @Test
    public void testInheritance_fieldOrderFromParentToChild() {
        Employee emp = new Employee(1L, "2024-01-01", "John", "13812345678", null);
        String result = emp.toString();

        int idIndex = result.indexOf("id=");
        int createTimeIndex = result.indexOf("createTime=");
        int nameIndex = result.indexOf("name=");
        int phoneIndex = result.indexOf("phone=");

        assertTrue("id should come before createTime", idIndex < createTimeIndex);
        assertTrue("createTime should come before name", createTimeIndex < nameIndex);
        assertTrue("name should come before phone", nameIndex < phoneIndex);
    }

    @Test
    public void testDesensitizable_toStringOverride() {
        Employee emp = new Employee(1L, "2024-01-01", "John", "13812345678", null);

        String toStringResult = emp.toString();
        String directResult = ToStringDesensitizeUtils.toDesensitizeString(emp);

        assertEquals("toString() should match toDesensitizeString()", directResult, toStringResult);
    }

    @Test
    public void testLombokCompatibleFormat() {
        PlainInfo plain = new PlainInfo("value1", "value2");
        String result = ToStringDesensitizeUtils.toDesensitizeString(plain);

        assertTrue("Should start with class name and parenthesis", result.startsWith("PlainInfo("));
        assertTrue("Should end with closing parenthesis", result.endsWith(")"));
        assertFalse("Should not use curly braces", result.contains("{"));
    }

    // ========================================================================
    // ENCRYPT action / DesensitizeEncryptor tests
    // ========================================================================

    @After
    public void clearEncryptor() {
        ToStringDesensitizeUtils.setEncryptor(null);
    }

    static class EncryptedUser {
        private String name;

        @Desensitize(action = DesensitizeAction.ENCRYPT)
        private String phone;

        @Desensitize(action = DesensitizeAction.ENCRYPT, type = DesensitizeType.ID_CARD)
        private String idCard;

        public EncryptedUser(String name, String phone, String idCard) {
            this.name = name;
            this.phone = phone;
            this.idCard = idCard;
        }
    }

    static class EncryptedCollectionHolder {
        @Desensitize(action = DesensitizeAction.ENCRYPT)
        private String[] secrets;

        @Desensitize(action = DesensitizeAction.ENCRYPT)
        private List<String> tokens;

        public EncryptedCollectionHolder(String[] secrets, List<String> tokens) {
            this.secrets = secrets;
            this.tokens = tokens;
        }
    }

    @Test
    public void testEncrypt_withEncryptor_encrypted() {
        ToStringDesensitizeUtils.setEncryptor(rawValue -> "ENC(" + rawValue + ")");

        EncryptedUser user = new EncryptedUser("test", "13812345678", "110101199001011234");
        String result = ToStringDesensitizeUtils.toDesensitizeString(user);

        assertTrue(result.contains("name=test"));
        assertTrue(result.contains("phone=ENC(13812345678)"));
        assertTrue(result.contains("idCard=ENC(110101199001011234)"));
    }

    @Test
    public void testEncrypt_withoutEncryptor_fallsBackToMask() {
        // No encryptor registered — should fall back to masking
        EncryptedUser user = new EncryptedUser("test", "13812345678", "110101199001011234");
        String result = ToStringDesensitizeUtils.toDesensitizeString(user);

        assertTrue("Should fall back to default mask", result.contains("phone=****"));
        assertTrue("Should fall back to default mask", result.contains("idCard=110101****1234"));
        assertFalse("Should not contain plaintext", result.contains("13812345678"));
        assertFalse("Should not contain plaintext", result.contains("110101199001011234"));
    }

    @Test
    public void testEncrypt_withEncryptor_handlesNullField() {
        ToStringDesensitizeUtils.setEncryptor(rawValue -> "ENC(" + rawValue + ")");

        EncryptedUser user = new EncryptedUser("test", null, null);
        String result = ToStringDesensitizeUtils.toDesensitizeString(user);

        assertTrue(result.contains("phone=null"));
        assertTrue(result.contains("idCard=null"));
    }

    @Test
    public void testEncrypt_withEncryptor_handlesEmptyField() {
        ToStringDesensitizeUtils.setEncryptor(rawValue -> "ENC(" + rawValue + ")");

        EncryptedUser user = new EncryptedUser("test", "", "");
        String result = ToStringDesensitizeUtils.toDesensitizeString(user);

        // Empty string should pass through without calling encryptor
        assertTrue(result.contains("phone="));
        assertTrue(result.contains("idCard="));
    }

    @Test
    public void testEncrypt_arrayAndList_encrypted() {
        ToStringDesensitizeUtils.setEncryptor(rawValue -> "ENC(" + rawValue + ")");

        EncryptedCollectionHolder holder = new EncryptedCollectionHolder(
            new String[]{"aaa", "bbb"},
            Arrays.asList("ccc", "ddd"));
        String result = ToStringDesensitizeUtils.toDesensitizeString(holder);

        assertTrue(result.contains("secrets=[ENC(aaa), ENC(bbb)]"));
        assertTrue(result.contains("tokens=[ENC(ccc), ENC(ddd)]"));
    }

    @Test
    public void testSetEncryptor_nullClearsReference() {
        ToStringDesensitizeUtils.setEncryptor(rawValue -> "ENC");
        assertTrue(ToStringDesensitizeUtils.getEncryptor() != null);

        ToStringDesensitizeUtils.setEncryptor(null);
        assertTrue(ToStringDesensitizeUtils.getEncryptor() == null);
    }

    @Test
    public void testGetEncryptor_defaultIsNull() {
        // After @After cleanup, encryptor should be null
        assertTrue(ToStringDesensitizeUtils.getEncryptor() == null);
    }

    @Test
    public void testEncrypt_encryptorReplaced_atRuntime() {
        ToStringDesensitizeUtils.setEncryptor(rawValue -> "V1(" + rawValue + ")");

        EncryptedUser user = new EncryptedUser("test", "123", null);
        String result1 = ToStringDesensitizeUtils.toDesensitizeString(user);
        assertTrue(result1.contains("phone=V1(123)"));

        // Replace encryptor at runtime
        ToStringDesensitizeUtils.setEncryptor(rawValue -> "V2(" + rawValue + ")");

        String result2 = ToStringDesensitizeUtils.toDesensitizeString(user);
        assertTrue(result2.contains("phone=V2(123)"));
    }
}
