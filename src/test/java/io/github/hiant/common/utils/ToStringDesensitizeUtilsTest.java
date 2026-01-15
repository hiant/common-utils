package io.github.hiant.common.utils;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for annotation-based desensitization utilities.
 * <p>
 * Tests {@link Desensitize} annotation, {@link DesensitizeType} enum,
 * and {@link ToStringDesensitizeUtils} functionality.
 */
public class ToStringDesensitizeUtilsTest {

    // ================================================================================
    // Test Data Classes
    // ================================================================================

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

    /**
     * Entity with hash option enabled.
     */
    static class SecureInfo {
        @Desensitize(type = DesensitizeType.MOBILE_PHONE, withHash = true)
        private String phone;

        public SecureInfo(String phone) {
            this.phone = phone;
        }
    }

    /**
     * Entity with no annotations.
     */
    static class PlainInfo {
        private String field1;
        private String field2;

        public PlainInfo(String field1, String field2) {
            this.field1 = field1;
            this.field2 = field2;
        }
    }

    // ================================================================================
    // ToStringDesensitizeUtils Tests
    // ================================================================================

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

    // ================================================================================
    // DesensitizeType Tests
    // ================================================================================

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
    public void testDesensitizeType_email_hasNoPreset() {
        assertFalse("EMAIL should have no prefix preset", DesensitizeType.EMAIL.getDefaultPrefix().isPresent());
        assertFalse("EMAIL should have no suffix preset", DesensitizeType.EMAIL.getDefaultSuffix().isPresent());
    }

    // ================================================================================
    // DesensitizationUtils.desensitizeEmail Tests
    // ================================================================================

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

    // ================================================================================
    // Cache Behavior Tests
    // ================================================================================

    @Test
    public void testToDesensitizeString_multipleCalls_useCache() {
        UserInfo user1 = new UserInfo("test1", "13812345678", null, null, null, null);
        UserInfo user2 = new UserInfo("test2", "13987654321", null, null, null, null);

        String result1 = ToStringDesensitizeUtils.toDesensitizeString(user1);
        String result2 = ToStringDesensitizeUtils.toDesensitizeString(user2);

        // Both should be desensitized correctly (cache should work)
        assertTrue("First user phone should be desensitized", result1.contains("phone=138****5678"));
        assertTrue("Second user phone should be desensitized", result2.contains("phone=139****4321"));
    }

    // ================================================================================
    // Inheritance Tests
    // ================================================================================

    /**
     * Base entity class for inheritance testing.
     */
    static class BaseEntity implements Desensitizable {
        private Long id;
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

    /**
     * Child entity extending BaseEntity with additional desensitized fields.
     */
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

    /**
     * Grandchild entity for multi-level inheritance testing.
     */
    static class Manager extends Employee {
        private String department;

        @Desensitize(type = DesensitizeType.EMAIL)
        private String workEmail;

        public Manager(Long id, String createTime, String name, String phone, String idCard,
                       String department, String workEmail) {
            super(id, createTime, name, phone, idCard);
            this.department = department;
            this.workEmail = workEmail;
        }
    }

    @Test
    public void testInheritance_childClassIncludesParentFields() {
        Employee emp = new Employee(1L, "2024-01-01", "John", "13812345678", "110101199001011234");
        String result = emp.toString();

        // Should include parent class fields
        assertTrue("Should contain id from parent", result.contains("id=1"));
        assertTrue("Should contain createTime from parent", result.contains("createTime=2024-01-01"));

        // Should include child class fields with desensitization
        assertTrue("Should contain name", result.contains("name=John"));
        assertTrue("Should contain desensitized phone", result.contains("phone=138****5678"));
        assertTrue("Should contain desensitized idCard", result.contains("idCard=110101****1234"));
    }

    @Test
    public void testInheritance_multiLevelHierarchy() {
        Manager mgr = new Manager(2L, "2024-02-01", "Jane", "13987654321", "320101198505051234",
                "Engineering", "jane.doe@company.com");
        String result = mgr.toString();

        // Should include all levels of hierarchy
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

        // Verify field order: parent fields should come before child fields
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

        // Verify toString() is overridden and returns desensitized output
        String toStringResult = emp.toString();
        String directResult = ToStringDesensitizeUtils.toDesensitizeString(emp);

        assertEquals("toString() should match toDesensitizeString()", directResult, toStringResult);
    }

    @Test
    public void testLombokCompatibleFormat() {
        PlainInfo plain = new PlainInfo("value1", "value2");
        String result = ToStringDesensitizeUtils.toDesensitizeString(plain);

        // Lombok format: ClassName(field1=value1, field2=value2)
        assertTrue("Should start with class name and parenthesis", result.startsWith("PlainInfo("));
        assertTrue("Should end with closing parenthesis", result.endsWith(")"));
        assertFalse("Should not use curly braces", result.contains("{"));
    }
}
