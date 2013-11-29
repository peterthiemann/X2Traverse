package de.unifr.acp.templates;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.unifr.acp.fst.Permission;
import de.unifr.acp.trafo.TransClass;

public class Global {
    static {
        try {
            logger = Logger.getLogger("de.unifr.acp.templates.Global");
            fh = new FileHandler("traverse.log");
            Global.logger.addHandler(Global.fh);
            Global.logger.setLevel(Level.ALL);
        } catch (SecurityException | IOException e) {
            throw new RuntimeException(e);
        }
    }
    private static Logger logger;
    private static FileHandler fh;
    
    public static final boolean enableDebugOutput = false;

    /**
     * A permission stack is a stack of (weak identity) maps from (Object x
     * String) to Permission.
     * 
     * Using an array deque as no null elements are required - for
     * contract-less/ignored methods no location permission is pushed/popped.
     */
    public static Deque<Map<Object, Map<String, Permission>>> locPermStack = new ArrayDeque<Map<Object, Map<String, Permission>>>();

    /**
     * A stack of (weak identity) object sets. Each set represents the newly
     * allocated locations since the last active permission was installed.
     * 
     * Using an array deque as no null elements are required - for
     * contract-less/ignored methods no new  permission is pushed/popped.
     */
    public static Deque<Set<Object>> newObjectsStack = new ArrayDeque<Set<Object>>();
    
    // possibly one more stack is required for debugging/logging/error messages
    
    
    /**
     * Returns the installed permission for the specified location. Defaults to
     * {@link Permission#READ_WRITE} if no permissions are enabled.
     * 
     * @param obj
     *            the location's object (null indicates a static field -
     *            unimplemented)
     * @param fieldName
     *            the location's fully qualified field name
     * @return the installed permission for the specified location
     */
    public static Permission installedPermission(Object obj, String fieldName) {
        if (enableDebugOutput) {
            System.out.println("installedPermissions(" + obj + ", " + fieldName
                    + ")");
            System.out.println("locPerms: " + locPermStack.peek());
        }
        Deque<Set<Object>> newObjectsStack = Global.newObjectsStack;
        if (!newObjectsStack.isEmpty()) {
            
            // consider topmost set of new objects
            if (newObjectsStack.peek().contains(obj)) {
                return Permission.READ_WRITE;
            }

            // consider topmost location permissions;
            // access to unmarked locations is forbidden
            Map<String, Permission> fieldPerm = Global.locPermStack.peek()
                    .get(obj);

            // in case there is a field permission map we expect all instance
            // fields to have an entry (once the implementation is completed ;-)
//            assert ((fieldPerm != null) ? fieldPerm.containsKey(fieldName)
//                    : true);
            return (fieldPerm != null) ? (fieldPerm.containsKey(fieldName) ? fieldPerm.get(fieldName) : Permission.NONE)
                    : Permission.NONE;

        } else {
            //assert (Global.locPermStack.isEmpty());

            // in case no contract is active resort to R/W permission
            return Permission.READ_WRITE;
        }
    }
    
    /**
     * Returns the installed permission for the specified location. Defaults to
     * {@link Permission#READ_WRITE} if no permissions are enabled.
     * 
     * @param obj
     *            the location's object (null indicates a static field -
     *            unimplemented)
     * @param fieldName
     *            the location's fully qualified field name
     * @return the installed permission for the specified location
     */
    public static Permission installedPermissionStackNotEmpty(Object obj, String fieldName) {
        if (enableDebugOutput) {
            System.out.println("installedPermissions(" + obj + ", " + fieldName
                    + ")");
            System.out.println("locPerms: " + locPermStack.peek());
        }
        Deque<Set<Object>> newObjectsStack = Global.newObjectsStack;
            
            // consider topmost set of new objects
            if (newObjectsStack.peek().contains(obj)) {
                return Permission.READ_WRITE;
            }

            // consider topmost location permissions;
            // access to unmarked locations is forbidden
            Map<String, Permission> fieldPerm = Global.locPermStack.peek()
                    .get(obj);

            // in case there is a field permission map we expect all instance
            // fields to have an entry (once the implementation is completed ;-)
//            assert ((fieldPerm != null) ? fieldPerm.containsKey(fieldName)
//                    : true);
            return (fieldPerm != null) ? (fieldPerm.containsKey(fieldName) ? fieldPerm.get(fieldName) : Permission.NONE)
                    : Permission.NONE;
    }
    
    public static void addNewObject(Object obj) {
        if (!newObjectsStack.isEmpty()) {
            newObjectsStack.peek().add(obj);
        }
    }
    
    public static void printViolation(Object obj, String qualifiedFieldName, String methodName,
            Permission effectivePerm, Permission requiredPerm) {
        System.out.println("ACCESS VIOLATION:");
        System.out.println("Instance: " + obj);
        System.out.println("Field: " + qualifiedFieldName);
        System.out.println("Effective permission: " + effectivePerm);
        System.out.println("Required permission: " + requiredPerm);
        System.out.println("in method "+ methodName);
        System.out.println(de.unifr.acp.templates.Global.locPermStack);
        System.out.println();
    }
}
