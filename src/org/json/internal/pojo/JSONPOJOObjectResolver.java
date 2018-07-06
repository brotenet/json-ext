package org.json.internal.pojo;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.json.exceptions.JSONIOException;


public class JSONPOJOObjectResolver extends JSONPOJOResolver
{
    private final ClassLoader classLoader;
    protected JSONPOJOReader.MissingFieldHandler missingFieldHandler;

    
    protected JSONPOJOObjectResolver(JSONPOJOReader reader, ClassLoader classLoader)
    {
        super(reader);
        this.classLoader = classLoader;
        missingFieldHandler = reader.getMissingFieldHandler();
    }

    
    public void traverseFields(final Deque<JSONPOJOElement<String, Object>> stack, final JSONPOJOElement<String, Object> jsonObj)
    {
        final Object javaMate = jsonObj.target;
        final Iterator<Map.Entry<String, Object>> i = jsonObj.entrySet().iterator();
        final Class cls = javaMate.getClass();

        while (i.hasNext())
        {
            Map.Entry<String, Object> e = i.next();
            String key = e.getKey();
            final Field field = JSONPOJOMetaUtils.getField(cls, key);
            Object rhs = e.getValue();
            if (field != null)
            {
                assignField(stack, jsonObj, field, rhs);
            }
            else if (missingFieldHandler != null)
            {
                handleMissingField(stack, jsonObj, rhs, key);
            }//else no handler so ignor.
        }
    }

    
    protected void assignField(final Deque<JSONPOJOElement<String, Object>> stack, final JSONPOJOElement jsonObj,
                               final Field field, final Object rhs)
    {
        final Object target = jsonObj.target;
        try
        {
            final Class fieldType = field.getType();
            if (rhs == null)
            {   // Logically clear field (allows null to be set against primitive fields, yielding their zero value.
                if (fieldType.isPrimitive())
                {
                    field.set(target, JSONPOJOMetaUtils.convert(fieldType, "0"));
                }
                else
                {
                    field.set(target, null);
                }
                return;
            }

            // If there is a "tree" of objects (e.g, Map<String, List<Person>>), the subobjects may not have an
            // @type on them, if the source of the JSON is from JSON.stringify().  Deep traverse the args and
            // mark @type on the items within the Maps and Collections, based on the parameterized type (if it
            // exists).
            if (rhs instanceof JSONPOJOElement)
            {
                if (field.getGenericType() instanceof ParameterizedType)
                {   // Only JsonObject instances could contain unmarked objects.
                    markUntypedObjects(field.getGenericType(), rhs, JSONPOJOMetaUtils.getDeepDeclaredFields(fieldType));
                }

                // Ensure .type field set on JsonObject
                final JSONPOJOElement job = (JSONPOJOElement) rhs;
                final String type = job.type;
                if (type == null || type.isEmpty())
                {
                    job.setType(fieldType.getName());
                }
            }

            Object special;
            if (rhs == JSONPOJOParser.EMPTY_OBJECT)
            {
                final JSONPOJOElement jObj = new JSONPOJOElement();
                jObj.type = fieldType.getName();
                Object value = createJavaObjectInstance(fieldType, jObj);
                field.set(target, value);
            }
            else if ((special = readIfMatching(rhs, fieldType, stack)) != null)
            {
                field.set(target, special);
            }
            else if (rhs.getClass().isArray())
            {    // LHS of assignment is an [] field or RHS is an array and LHS is Object
                final Object[] elements = (Object[]) rhs;
                JSONPOJOElement<String, Object> jsonArray = new JSONPOJOElement<String, Object>();
                if (char[].class == fieldType)
                {   // Specially handle char[] because we are writing these
                    // out as UTF8 strings for compactness and speed.
                    if (elements.length == 0)
                    {
                        field.set(target, new char[]{});
                    }
                    else
                    {
                        field.set(target, ((String) elements[0]).toCharArray());
                    }
                }
                else
                {
                    jsonArray.put("@items", elements);
                    createJavaObjectInstance(fieldType, jsonArray);
                    field.set(target, jsonArray.target);
                    stack.addFirst(jsonArray);
                }
            }
            else if (rhs instanceof JSONPOJOElement)
            {
                final JSONPOJOElement<String, Object> jObj = (JSONPOJOElement) rhs;
                final Long ref = jObj.getReferenceId();

                if (ref != null)
                {    // Correct field references
                    final JSONPOJOElement refObject = getReferencedObj(ref);

                    if (refObject.target != null)
                    {
                        field.set(target, refObject.target);
                    }
                    else
                    {
                        unresolvedRefs.add(new UnresolvedReference(jsonObj, field.getName(), ref));
                    }
                }
                else
                {    // Assign ObjectMap's to Object (or derived) fields
                    field.set(target, createJavaObjectInstance(fieldType, jObj));
                    if (!JSONPOJOMetaUtils.isLogicalPrimitive(jObj.getTargetClass()))
                    {
                        stack.addFirst((JSONPOJOElement) rhs);
                    }
                }
            }
            else
            {
                if (JSONPOJOMetaUtils.isPrimitive(fieldType))
                {
                    field.set(target, JSONPOJOMetaUtils.convert(fieldType, rhs));
                }
                else if (rhs instanceof String && "".equals(((String) rhs).trim()) && fieldType != String.class)
                {   // Allow "" to null out a non-String field
                    field.set(target, null);
                }
                else
                {
                    field.set(target, rhs);
                }
            }
        }
        catch (Exception e)
        {
            String message = e.getClass().getSimpleName() + " setting field '" + field.getName() + "' on target: " + safeToString(target) + " with value: " + rhs;
            if (JSONPOJOMetaUtils.loadClassException != null)
            {
                message += " Caused by: " + JSONPOJOMetaUtils.loadClassException + " (which created a LinkedHashMap instead of the desired class)";
            }
            throw new JSONIOException(message, e);
        }
    }


    
    protected void handleMissingField(final Deque<JSONPOJOElement<String, Object>> stack, final JSONPOJOElement jsonObj, final Object rhs,
                                      final String missingField)
    {
        final Object target = jsonObj.target;
        try
        {
            if (rhs == null)
            { // Logically clear field (allows null to be set against primitive fields, yielding their zero value.
                storeMissingField(target, missingField, null);
                return;
            }

            // we have a jsonobject with a type
            Object special;
            if (rhs == JSONPOJOParser.EMPTY_OBJECT)
            {
                storeMissingField(target, missingField, null);
            }
            else if ((special = readIfMatching(rhs, null, stack)) != null)
            {
                storeMissingField(target, missingField, special);
            }
            else if (rhs.getClass().isArray())
            {
                // impossible to determine the array type.
                storeMissingField(target, missingField, null);
            }
            else if (rhs instanceof JSONPOJOElement)
            {
                final JSONPOJOElement<String, Object> jObj = (JSONPOJOElement) rhs;
                final Long ref = jObj.getReferenceId();

                if (ref != null)
                { // Correct field references
                    final JSONPOJOElement refObject = getReferencedObj(ref);
                    storeMissingField(target, missingField, refObject.target);
                }
                else
                {   // Assign ObjectMap's to Object (or derived) fields
                    // check that jObj as a type
                    if (jObj.getType() != null)
                    {
                        Object createJavaObjectInstance = createJavaObjectInstance(null, jObj);
                        if (!JSONPOJOMetaUtils.isLogicalPrimitive(jObj.getTargetClass()))
                        {
                            stack.addFirst((JSONPOJOElement) rhs);
                        }
                        storeMissingField(target, missingField, createJavaObjectInstance);
                    } 
                    else //no type found, just notify.
                    {
                        storeMissingField(target, missingField, null);
                    }
                }
            }
            else
            {
                storeMissingField(target, missingField, rhs);
            }
        }
        catch (Exception e)
        {
            String message = e.getClass().getSimpleName() + " missing field '" + missingField + "' on target: "
                    + safeToString(target) + " with value: " + rhs;
            if (JSONPOJOMetaUtils.loadClassException != null)
            {
                message += " Caused by: " + JSONPOJOMetaUtils.loadClassException
                        + " (which created a LinkedHashMap instead of the desired class)";
            }
            throw new JSONIOException(message, e);
        }
    }

    
    private void storeMissingField(Object target, String missingField, Object value)
    {
        missingFields.add(new Missingfields(target, missingField, value));
    }


    
    private static String safeToString(Object o)
    {
        if (o == null)
        {
            return "null";
        }
        try
        {
            return o.toString();
        }
        catch (Exception e)
        {
            return o.getClass().toString();
        }
    }

    
    protected void traverseCollection(final Deque<JSONPOJOElement<String, Object>> stack, final JSONPOJOElement<String, Object> jsonObj)
    {
        final Object[] items = jsonObj.getArray();
        if (items == null || items.length == 0)
        {
            return;
        }
        final Collection col = (Collection) jsonObj.target;
        final boolean isList = col instanceof List;
        int idx = 0;

        for (final Object element : items)
        {
            Object special;
            if (element == null)
            {
                col.add(null);
            }
            else if (element == JSONPOJOParser.EMPTY_OBJECT)
            {   // Handles {}
                col.add(new JSONPOJOElement());
            }
            else if ((special = readIfMatching(element, null, stack)) != null)
            {
                col.add(special);
            }
            else if (element instanceof String || element instanceof Boolean || element instanceof Double || element instanceof Long)
            {    // Allow Strings, Booleans, Longs, and Doubles to be "inline" without Java object decoration (@id, @type, etc.)
                col.add(element);
            }
            else if (element.getClass().isArray())
            {
                final JSONPOJOElement jObj = new JSONPOJOElement();
                jObj.put("@items", element);
                createJavaObjectInstance(Object.class, jObj);
                col.add(jObj.target);
                convertMapsToObjects(jObj);
            }
            else // if (element instanceof JsonObject)
            {
                final JSONPOJOElement jObj = (JSONPOJOElement) element;
                final Long ref = jObj.getReferenceId();

                if (ref != null)
                {
                    JSONPOJOElement refObject = getReferencedObj(ref);

                    if (refObject.target != null)
                    {
                        col.add(refObject.target);
                    }
                    else
                    {
                        unresolvedRefs.add(new UnresolvedReference(jsonObj, idx, ref));
                        if (isList)
                        {   // Indexable collection, so set 'null' as element for now - will be patched in later.
                            col.add(null);
                        }
                    }
                }
                else
                {
                    createJavaObjectInstance(Object.class, jObj);

                    if (!JSONPOJOMetaUtils.isLogicalPrimitive(jObj.getTargetClass()))
                    {
                        convertMapsToObjects(jObj);
                    }
                    col.add(jObj.target);
                }
            }
            idx++;
        }

        jsonObj.remove("@items");   // Reduce memory required during processing
    }

    
    protected void traverseArray(final Deque<JSONPOJOElement<String, Object>> stack, final JSONPOJOElement<String, Object> jsonObj)
    {
        final int len = jsonObj.getLength();
        if (len == 0)
        {
            return;
        }

        final Class compType = jsonObj.getComponentType();

        if (char.class == compType)
        {
            return;
        }

        if (byte.class == compType)
        {   // Handle byte[] special for performance boost.
            jsonObj.moveBytesToMate();
            jsonObj.clearArray();
            return;
        }

        final boolean isPrimitive = JSONPOJOMetaUtils.isPrimitive(compType);
        final Object array = jsonObj.target;
        final Object[] items =  jsonObj.getArray();

        for (int i=0; i < len; i++)
        {
            final Object element = items[i];

            Object special;
            if (element == null)
            {
                Array.set(array, i, null);
            }
            else if (element == JSONPOJOParser.EMPTY_OBJECT)
            {    // Use either explicitly defined type in ObjectMap associated to JSON, or array component type.
                Object arrayElement = createJavaObjectInstance(compType, new JSONPOJOElement());
                Array.set(array, i, arrayElement);
            }
            else if ((special = readIfMatching(element, compType, stack)) != null)
            {
                Array.set(array, i, special);
            }
            else if (isPrimitive)
            {   // Primitive component type array
                Array.set(array, i, JSONPOJOMetaUtils.convert(compType, element));
            }
            else if (element.getClass().isArray())
            {   // Array of arrays
                if (char[].class == compType)
                {   // Specially handle char[] because we are writing these
                    // out as UTF-8 strings for compactness and speed.
                    Object[] jsonArray = (Object[]) element;
                    if (jsonArray.length == 0)
                    {
                        Array.set(array, i, new char[]{});
                    }
                    else
                    {
                        final String value = (String) jsonArray[0];
                        final int numChars = value.length();
                        final char[] chars = new char[numChars];
                        for (int j = 0; j < numChars; j++)
                        {
                            chars[j] = value.charAt(j);
                        }
                        Array.set(array, i, chars);
                    }
                }
                else
                {
                    JSONPOJOElement<String, Object> jsonObject = new JSONPOJOElement<String, Object>();
                    jsonObject.put("@items", element);
                    Array.set(array, i, createJavaObjectInstance(compType, jsonObject));
                    stack.addFirst(jsonObject);
                }
            }
            else if (element instanceof JSONPOJOElement)
            {
                JSONPOJOElement<String, Object> jsonObject = (JSONPOJOElement<String, Object>) element;
                Long ref = jsonObject.getReferenceId();

                if (ref != null)
                {    // Connect reference
                    JSONPOJOElement refObject = getReferencedObj(ref);
                    if (refObject.target != null)
                    {   // Array element with reference to existing object
                        Array.set(array, i, refObject.target);
                    }
                    else
                    {    // Array with a forward reference as an element
                        unresolvedRefs.add(new UnresolvedReference(jsonObj, i, ref));
                    }
                }
                else
                {    // Convert JSON HashMap to Java Object instance and assign values
                    Object arrayElement = createJavaObjectInstance(compType, jsonObject);
                    Array.set(array, i, arrayElement);
                    if (!JSONPOJOMetaUtils.isLogicalPrimitive(arrayElement.getClass()))
                    {    // Skip walking primitives, primitive wrapper classes, Strings, and Classes
                        stack.addFirst(jsonObject);
                    }
                }
            }
            else
            {
                if (element instanceof String && "".equals(((String) element).trim()) && compType != String.class && compType != Object.class)
                {   // Allow an entry of "" in the array to set the array element to null, *if* the array type is NOT String[] and NOT Object[]
                    Array.set(array, i, null);
                }
                else
                {
                    Array.set(array, i, element);
                }
            }
        }
        jsonObj.clearArray();
    }

    
    protected Object readIfMatching(final Object o, final Class compType, final Deque<JSONPOJOElement<String, Object>> stack)
    {
        if (o == null)
        {
            throw new JSONIOException("Bug in json-io, null must be checked before calling this method.");
        }

        if (compType != null && notCustom(compType))
        {
            return null;
        }

        final boolean isJsonObject = o instanceof JSONPOJOElement;
        if (!isJsonObject && compType == null)
        {   // If not a JsonObject (like a Long that represents a date, then compType must be set)
            return null;
        }

        Class c;
        boolean needsType = false;

        // Set up class type to check against reader classes (specified as @type, or jObj.target, or compType)
        if (isJsonObject)
        {
            JSONPOJOElement jObj = (JSONPOJOElement) o;
            if (jObj.isReference())
            {
                return null;
            }

            if (jObj.target == null)
            {   // '@type' parameter used (not target instance)
                String typeStr = null;
                try
                {
                    Object type =  jObj.type;
                    if (type != null)
                    {
                        typeStr = (String) type;
                        c = JSONPOJOMetaUtils.classForName((String) type, classLoader);
                    }
                    else
                    {
                        if (compType != null)
                        {
                            c = compType;
                            needsType = true;
                        }
                        else
                        {
                            return null;
                        }
                    }
                    createJavaObjectInstance(c, jObj);
                }
                catch(Exception e)
                {
                    throw new JSONIOException("Class listed in @type [" + typeStr + "] is not found", e);
                }
            }
            else
            {   // Type inferred from target object
                c = jObj.target.getClass();
            }
        }
        else
        {
            c = compType;
        }

        if (notCustom(c))
        {
            return null;
        }

        JSONPOJOReader.JsonClassReaderBase closestReader = getCustomReader(c);

        if (closestReader == null)
        {
            return null;
        }

        if (needsType)
        {
            ((JSONPOJOElement)o).setType(c.getName());
        }

        Object read;
        if (closestReader instanceof JSONPOJOReader.JsonClassReaderEx)
        {
            read = ((JSONPOJOReader.JsonClassReaderEx)closestReader).read(o, stack, getReader().getArgs());
        }
        else
        {
            read = ((JSONPOJOReader.JsonClassReader)closestReader).read(o, stack);
        }
		return read;
    }

    private void markUntypedObjects(final Type type, final Object rhs, final Map<String, Field> classFields)
    {
        final Deque<Object[]> stack = new ArrayDeque<Object[]>();
        stack.addFirst(new Object[] {type, rhs});

        while (!stack.isEmpty())
        {
            Object[] item = stack.removeFirst();
            final Type t = (Type) item[0];
            final Object instance = item[1];
            if (t instanceof ParameterizedType)
            {
                final Class clazz = getRawType(t);
                final ParameterizedType pType = (ParameterizedType)t;
                final Type[] typeArgs = pType.getActualTypeArguments();

                if (typeArgs == null || typeArgs.length < 1 || clazz == null)
                {
                    continue;
                }

                stampTypeOnJsonObject(instance, t);

                if (Map.class.isAssignableFrom(clazz))
                {
                    Map map = (Map) instance;
                    if (!map.containsKey("@keys") && !map.containsKey("@items") && map instanceof JSONPOJOElement)
                    {   // Maps created in Javascript will come over without @keys / @items.
                        convertMapToKeysItems((JSONPOJOElement) map);
                    }

                    Object[] keys = (Object[])map.get("@keys");
                    getTemplateTraverseWorkItem(stack, keys, typeArgs[0]);

                    Object[] items = (Object[])map.get("@items");
                    getTemplateTraverseWorkItem(stack, items, typeArgs[1]);
                }
                else if (Collection.class.isAssignableFrom(clazz))
                {
                    if (instance instanceof Object[])
                    {
                        Object[] array = (Object[]) instance;
                        for (int i=0; i < array.length; i++)
                        {
                            Object vals = array[i];
                            stack.addFirst(new Object[]{t, vals});

                            if (vals instanceof JSONPOJOElement)
                            {
                                stack.addFirst(new Object[]{t, vals});
                            }
                            else if (vals instanceof Object[])
                            {
                                JSONPOJOElement coll = new JSONPOJOElement();
                                coll.type = clazz.getName();
                                List items = Arrays.asList((Object[]) vals);
                                coll.put("@items", items.toArray());
                                stack.addFirst(new Object[]{t, items});
                                array[i] = coll;
                            }
                            else
                            {
                                stack.addFirst(new Object[]{t, vals});
                            }
                        }
                    }
                    else if (instance instanceof Collection)
                    {
                        final Collection col = (Collection)instance;
                        for (Object o : col)
                        {
                            stack.addFirst(new Object[]{typeArgs[0], o});
                        }
                    }
                    else if (instance instanceof JSONPOJOElement)
                    {
                        final JSONPOJOElement jObj = (JSONPOJOElement) instance;
                        final Object[] array = jObj.getArray();
                        if (array != null)
                        {
                            for (Object o : array)
                            {
                                stack.addFirst(new Object[]{typeArgs[0], o});
                            }
                        }
                    }
                }
                else
                {
                    if (instance instanceof JSONPOJOElement)
                    {
                        final JSONPOJOElement<String, Object> jObj = (JSONPOJOElement) instance;

                        for (Map.Entry<String, Object> entry : jObj.entrySet())
                        {
                            final String fieldName = entry.getKey();
                            if (!fieldName.startsWith("this$"))
                            {
                                Field field = classFields.get(fieldName);

                                if (field != null && (field.getType().getTypeParameters().length > 0 || field.getGenericType() instanceof TypeVariable))
                                {
                                    stack.addFirst(new Object[]{typeArgs[0], entry.getValue()});
                                }
                            }
                        }
                    }
                }
            }
            else
            {
                stampTypeOnJsonObject(instance, t);
            }
        }
    }

    private static void getTemplateTraverseWorkItem(final Deque<Object[]> stack, final Object[] items, final Type type)
    {
        if (items == null || items.length < 1)
        {
            return;
        }
        Class rawType = getRawType(type);
        if (rawType != null && Collection.class.isAssignableFrom(rawType))
        {
            stack.add(new Object[]{type, items});
        }
        else
        {
            for (Object o : items)
            {
                stack.add(new Object[]{type, o});
            }
        }
    }

    // Mark 'type' on JsonObject when the type is missing and it is a 'leaf'
    // node (no further subtypes in it's parameterized type definition)
    private static void stampTypeOnJsonObject(final Object o, final Type t)
    {
        Class clazz = t instanceof Class ? (Class)t : getRawType(t);

        if (o instanceof JSONPOJOElement && clazz != null)
        {
            JSONPOJOElement jObj = (JSONPOJOElement) o;
            if ((jObj.type == null || jObj.type.isEmpty()) && jObj.target == null)
            {
                jObj.type = clazz.getName();
            }
        }
    }

    
    public static Class getRawType(final Type t)
    {
        if (t instanceof ParameterizedType)
        {
            ParameterizedType pType = (ParameterizedType) t;

            if (pType.getRawType() instanceof Class)
            {
                return (Class) pType.getRawType();
            }
        }
        return null;
    }
}
