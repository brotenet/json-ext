package org.json.internal.pojo;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.json.exceptions.JSONIOException;
import org.json.internal.pojo.JSONPOJOReader.MissingFieldHandler;


abstract class JSONPOJOResolver
{
    final Collection<UnresolvedReference> unresolvedRefs = new ArrayList<UnresolvedReference>();
    protected final JSONPOJOReader reader;
    private static final NullClass nullReader = new NullClass();
    final Map<Class, JSONPOJOReader.JsonClassReaderBase> readerCache = new HashMap<Class, JSONPOJOReader.JsonClassReaderBase>();
    private final Collection<Object[]> prettyMaps = new ArrayList<Object[]>();
    private final boolean useMaps;
    private final Object unknownClass;
    private final boolean failOnUnknownType;
    private final static Map<String, Class> coercedTypes = new LinkedHashMap<String, Class>();
    // store the missing field found during deserialization to notify any client after the complete resolution is done
    protected final Collection<Missingfields> missingFields = new ArrayList<JSONPOJOResolver.Missingfields>();

    static {
        coercedTypes.put("java.util.Arrays$ArrayList", ArrayList.class);
        coercedTypes.put("java.util.LinkedHashMap$LinkedKeySet", LinkedHashSet.class);
        coercedTypes.put("java.util.LinkedHashMap$LinkedValues", ArrayList.class);
        coercedTypes.put("java.util.HashMap$KeySet", HashSet.class);
        coercedTypes.put("java.util.HashMap$Values", ArrayList.class);
        coercedTypes.put("java.util.TreeMap$KeySet", TreeSet.class);
        coercedTypes.put("java.util.TreeMap$Values", ArrayList.class);
        coercedTypes.put("java.util.concurrent.ConcurrentHashMap$KeySet", LinkedHashSet.class);
        coercedTypes.put("java.util.concurrent.ConcurrentHashMap$KeySetView", LinkedHashSet.class);
        coercedTypes.put("java.util.concurrent.ConcurrentHashMap$Values", ArrayList.class);
        coercedTypes.put("java.util.concurrent.ConcurrentHashMap$ValuesView", ArrayList.class);
        coercedTypes.put("java.util.concurrent.ConcurrentSkipListMap$KeySet", LinkedHashSet.class);
        coercedTypes.put("java.util.concurrent.ConcurrentSkipListMap$Values", ArrayList.class);
        coercedTypes.put("java.util.IdentityHashMap$KeySet", LinkedHashSet.class);
        coercedTypes.put("java.util.IdentityHashMap$Values", ArrayList.class);
    }

    
    static final class UnresolvedReference
    {
        private final JSONPOJOElement referencingObj;
        private String field;
        private final long refId;
        private int index = -1;

        UnresolvedReference(JSONPOJOElement referrer, String fld, long id)
        {
            referencingObj = referrer;
            field = fld;
            refId = id;
        }

        UnresolvedReference(JSONPOJOElement referrer, int idx, long id)
        {
            referencingObj = referrer;
            index = idx;
            refId = id;
        }
    }

    
    protected static class Missingfields
    {
        private Object target;
        private String fieldName;
        private Object value;

        public Missingfields(Object target, String fieldName, Object value)
        {
            this.target = target;
            this.fieldName = fieldName;
            this.value = value;
        }
    }

    
    private static final class NullClass implements JSONPOJOReader.JsonClassReaderBase  { }

    protected JSONPOJOResolver(JSONPOJOReader reader)
    {
        this.reader = reader;
        Map<String, Object> optionalArgs = reader.getArgs();
        optionalArgs.put(JSONPOJOReader.OBJECT_RESOLVER, this);
        useMaps = Boolean.TRUE.equals(optionalArgs.get(JSONPOJOReader.USE_MAPS));
        unknownClass = optionalArgs.containsKey(JSONPOJOReader.UNKNOWN_OBJECT) ? optionalArgs.get(JSONPOJOReader.UNKNOWN_OBJECT) : null;
        failOnUnknownType = Boolean.TRUE.equals(optionalArgs.get(JSONPOJOReader.FAIL_ON_UNKNOWN_TYPE));
    }

    protected JSONPOJOReader getReader()
    {
        return reader;
    }

    
    protected Object convertMapsToObjects(final JSONPOJOElement<String, Object> root)
    {
        final Deque<JSONPOJOElement<String, Object>> stack = new ArrayDeque<JSONPOJOElement<String, Object>>();
        stack.addFirst(root);

        while (!stack.isEmpty())
        {
            final JSONPOJOElement<String, Object> jsonObj = stack.removeFirst();

            if (jsonObj.isArray())
            {
                traverseArray(stack, jsonObj);
            }
            else if (jsonObj.isCollection())
            {
                traverseCollection(stack, jsonObj);
            }
            else if (jsonObj.isMap())
            {
                traverseMap(stack, jsonObj);
            }
            else
            {
                Object special;
                if ((special = readIfMatching(jsonObj, null, stack)) != null)
                {
                    jsonObj.target = special;
                }
                else
                {
                    traverseFields(stack, jsonObj);
                }
            }
        }
        return root.target;
    }

    protected abstract Object readIfMatching(final Object o, final Class compType, final Deque<JSONPOJOElement<String, Object>> stack);

    public abstract void traverseFields(Deque<JSONPOJOElement<String, Object>> stack, JSONPOJOElement<String, Object> jsonObj);

    protected abstract void traverseCollection(Deque<JSONPOJOElement<String, Object>> stack, JSONPOJOElement<String, Object> jsonObj);

    protected abstract void traverseArray(Deque<JSONPOJOElement<String, Object>> stack, JSONPOJOElement<String, Object> jsonObj);

    protected void cleanup()
    {
        patchUnresolvedReferences();
        rehashMaps();
        reader.getObjectsRead().clear();
        unresolvedRefs.clear();
        prettyMaps.clear();
        readerCache.clear();
        handleMissingFields();
    }

    // calls the missing field handler if any for each recorded missing field.
    private void handleMissingFields()
    {
        MissingFieldHandler missingFieldHandler = reader.getMissingFieldHandler();
        if (missingFieldHandler != null)
        {
            for (Missingfields mf : missingFields)
            {
                missingFieldHandler.fieldMissing(mf.target, mf.fieldName, mf.value);
            }
        }//else no handler so ignore.
    }

    
    protected void traverseMap(Deque<JSONPOJOElement<String, Object>> stack, JSONPOJOElement<String, Object> jsonObj)
    {
        // Convert @keys to a Collection of Java objects.
        convertMapToKeysItems(jsonObj);
        final Object[] keys = (Object[]) jsonObj.get("@keys");
        final Object[] items = jsonObj.getArray();

        if (keys == null || items == null)
        {
            if (keys != items)
            {
                throw new JSONIOException("Map written where one of @keys or @items is empty");
            }
            return;
        }

        final int size = keys.length;
        if (size != items.length)
        {
            throw new JSONIOException("Map written with @keys and @items entries of different sizes");
        }

        Object[] mapKeys = buildCollection(stack, keys, size);
        Object[] mapValues = buildCollection(stack, items, size);

        // Save these for later so that unresolved references inside keys or values
        // get patched first, and then build the Maps.
        prettyMaps.add(new Object[]{jsonObj, mapKeys, mapValues});
    }

    private static Object[] buildCollection(Deque<JSONPOJOElement<String, Object>> stack, Object[] items, int size)
    {
        final JSONPOJOElement<String, Object> jsonCollection = new JSONPOJOElement<String, Object>();
        jsonCollection.put("@items", items);
        final Object[] javaKeys = new Object[size];
        jsonCollection.target = javaKeys;
        stack.addFirst(jsonCollection);
        return javaKeys;
    }

    
    protected static void convertMapToKeysItems(final JSONPOJOElement<String, Object> map)
    {
        if (!map.containsKey("@keys") && !map.isReference())
        {
            final Object[] keys = new Object[map.size()];
            final Object[] values = new Object[map.size()];
            int i = 0;

            for (Object e : map.entrySet())
            {
                final Map.Entry entry = (Map.Entry) e;
                keys[i] = entry.getKey();
                values[i] = entry.getValue();
                i++;
            }
            String saveType = map.getType();
            map.clear();
            map.setType(saveType);
            map.put("@keys", keys);
            map.put("@items", values);
        }
    }

    
    protected Object createJavaObjectInstance(Class clazz, JSONPOJOElement jsonObj)
    {
        final boolean useMapsLocal = useMaps;
        String type = jsonObj.type;

        // We can't set values to an Object, so well try to use the contained type instead
		if ("java.lang.Object".equals(type))
        {
			Object value = jsonObj.get("value");
        	if (jsonObj.keySet().size() == 1 && value != null)
            {
        		type = value.getClass().getName();
        	}
        }

        Object mate;

        // @type always takes precedence over inferred Java (clazz) type.
        if (type != null)
        {    // @type is explicitly set, use that as it always takes precedence
            Class c;
            try
            {
                c = JSONPOJOMetaUtils.classForName(type, reader.getClassLoader(), failOnUnknownType);
            }
            catch (Exception e)
            {
                if (useMapsLocal)
                {
                    jsonObj.type = null;
                    jsonObj.target = null;
                    return jsonObj;
                }
                else
                {
                    String name = clazz == null ? "null" : clazz.getName();
                    throw new JSONIOException("Unable to create class: " + name, e);
                }
            }
            if (c.isArray())
            {    // Handle []
                Object[] items = jsonObj.getArray();
                int size = (items == null) ? 0 : items.length;
                if (c == char[].class)
                {
                    jsonObj.moveCharsToMate();
                    mate = jsonObj.target;
                }
                else
                {
                    mate = Array.newInstance(c.getComponentType(), size);
                }
            }
            else
            {    // Handle regular field.object reference
                if (JSONPOJOMetaUtils.isPrimitive(c))
                {
                    mate = JSONPOJOMetaUtils.convert(c, jsonObj.get("value"));
                }
                else if (c == Class.class)
                {
                    mate = JSONPOJOMetaUtils.classForName((String) jsonObj.get("value"), reader.getClassLoader());
                }
                else if (c.isEnum())
                {
                    mate = getEnum(c, jsonObj);
                }
                else if (Enum.class.isAssignableFrom(c)) // anonymous subclass of an enum
                {
                    mate = getEnum(c.getSuperclass(), jsonObj);
                }
                else if (EnumSet.class.isAssignableFrom(c))
                {
                    mate = getEnumSet(c, jsonObj);
                }
                else if ((mate = coerceCertainTypes(c.getName())) != null)
                {   // if coerceCertainTypes() returns non-null, it did the work
                }
                else
                {
                    mate = newInstance(c, jsonObj);
                }
            }
        }
        else
        {    // @type, not specified, figure out appropriate type
            Object[] items = jsonObj.getArray();

            // if @items is specified, it must be an [] type.
            // if clazz.isArray(), then it must be an [] type.
            if (clazz.isArray() || (items != null && clazz == Object.class && !jsonObj.containsKey("@keys")))
            {
                int size = (items == null) ? 0 : items.length;
                mate = Array.newInstance(clazz.isArray() ? clazz.getComponentType() : Object.class, size);
            }
            else if (clazz.isEnum())
            {
                mate = getEnum(clazz, jsonObj);
            }
            else if (Enum.class.isAssignableFrom(clazz)) // anonymous subclass of an enum
            {
                mate = getEnum(clazz.getSuperclass(), jsonObj);
            }
            else if (EnumSet.class.isAssignableFrom(clazz)) // anonymous subclass of an enum
            {
                mate = getEnumSet(clazz, jsonObj);
            }
            else if ((mate = coerceCertainTypes(clazz.getName())) != null)
            {   // if coerceCertainTypes() returns non-null, it did the work
            }
            else if (clazz == Object.class && !useMapsLocal)
            {
                if (unknownClass == null)
                {
                    mate = new JSONPOJOElement();
                    ((JSONPOJOElement)mate).type = Map.class.getName();
                }
                else if (unknownClass instanceof String)
                {
                    mate = newInstance(JSONPOJOMetaUtils.classForName(((String)unknownClass).trim(), reader.getClassLoader()), jsonObj);
                }
                else
                {
                    throw new JSONIOException("Unable to determine object type at column: " + jsonObj.col + ", line: " + jsonObj.line + ", content: " + jsonObj);
                }
            }
            else
            {
                mate = newInstance(clazz, jsonObj);
            }
        }
        jsonObj.target = mate;
        return jsonObj.target;
    }

    protected Object coerceCertainTypes(String type)
    {
        Class clazz = coercedTypes.get(type);
        if (clazz == null)
        {
            return null;
        }

        return JSONPOJOMetaUtils.newInstance(clazz);
    }

    protected JSONPOJOElement getReferencedObj(Long ref)
    {
        JSONPOJOElement refObject = reader.getObjectsRead().get(ref);
        if (refObject == null)
        {
            throw new JSONIOException("Forward reference @ref: " + ref + ", but no object defined (@id) with that value");
        }
        return refObject;
    }

    protected JSONPOJOReader.JsonClassReaderBase getCustomReader(Class c)
    {
        JSONPOJOReader.JsonClassReaderBase reader = readerCache.get(c);
        if (reader == null)
        {
            reader = forceGetCustomReader(c);
            readerCache.put(c, reader);
        }
        return reader == nullReader ? null : reader;
    }

    private JSONPOJOReader.JsonClassReaderBase forceGetCustomReader(Class c)
    {
        JSONPOJOReader.JsonClassReaderBase closestReader = nullReader;
        int minDistance = Integer.MAX_VALUE;

        for (Map.Entry<Class, JSONPOJOReader.JsonClassReaderBase> entry : getReaders().entrySet())
        {
            Class clz = entry.getKey();
            if (clz == c)
            {
                return entry.getValue();
            }
            int distance = JSONPOJOMetaUtils.getDistance(clz, c);
            if (distance < minDistance)
            {
                minDistance = distance;
                closestReader = entry.getValue();
            }
        }
        return closestReader;
    }

    
    private Object getEnum(Class c, JSONPOJOElement jsonObj)
    {
        try
        {
            return Enum.valueOf(c, (String) jsonObj.get("name"));
        }
        catch (Exception e)
        {   // In case the enum class has it's own 'name' member variable (shadowing the 'name' variable on Enum)
            return Enum.valueOf(c, (String) jsonObj.get("java.lang.Enum.name"));
        }
    }

    
    private Object getEnumSet(Class c, JSONPOJOElement<String, Object> jsonObj)
    {
        Object[] items = jsonObj.getArray();
        if (items == null || items.length == 0)
        {
            return newInstance(c, jsonObj);
        }
        JSONPOJOElement item = (JSONPOJOElement) items[0];
        String type = item.getType();
        Class enumClass = JSONPOJOMetaUtils.classForName(type, reader.getClassLoader());
        EnumSet enumSet = null;
        for (Object objectItem : items)
        {
            item = (JSONPOJOElement) objectItem;
            Enum enumItem = (Enum) getEnum(enumClass, item);
            if (enumSet == null)
            {   // Lazy init the EnumSet
                enumSet = EnumSet.of(enumItem);
            }
            else
            {
                enumSet.add(enumItem);
            }
        }
        return enumSet;
    }

    
    protected void patchUnresolvedReferences()
    {
        Iterator i = unresolvedRefs.iterator();
        while (i.hasNext())
        {
            UnresolvedReference ref = (UnresolvedReference) i.next();
            Object objToFix = ref.referencingObj.target;
            JSONPOJOElement objReferenced = reader.getObjectsRead().get(ref.refId);

            if (ref.index >= 0)
            {    // Fix []'s and Collections containing a forward reference.
                if (objToFix instanceof List)
                {   // Patch up Indexable Collections
                    List list = (List) objToFix;
                    list.set(ref.index, objReferenced.target);
                }
                else if (objToFix instanceof Collection)
                {   // Add element (since it was not indexable, add it to collection)
                    Collection col = (Collection) objToFix;
                    col.add(objReferenced.target);
                }
                else
                {
                    Array.set(objToFix, ref.index, objReferenced.target);        // patch array element here
                }
            }
            else
            {    // Fix field forward reference
                Field field = JSONPOJOMetaUtils.getField(objToFix.getClass(), ref.field);
                if (field != null)
                {
                    try
                    {
                        field.set(objToFix, objReferenced.target);               // patch field here
                    }
                    catch (Exception e)
                    {
                        throw new JSONIOException("Error setting field while resolving references '" + field.getName() + "', @ref = " + ref.refId, e);
                    }
                }
            }

            i.remove();
        }
    }

    
    protected void rehashMaps()
    {
        final boolean useMapsLocal = useMaps;
        for (Object[] mapPieces : prettyMaps)
        {
            JSONPOJOElement jObj = (JSONPOJOElement) mapPieces[0];
            Object[] javaKeys, javaValues;
            Map map;

            if (useMapsLocal)
            {   // Make the @keys be the actual keys of the map.
                map = jObj;
                javaKeys = (Object[]) jObj.remove("@keys");
                javaValues = (Object[]) jObj.remove("@items");
            }
            else
            {
                map = (Map) jObj.target;
                javaKeys = (Object[]) mapPieces[1];
                javaValues = (Object[]) mapPieces[2];
                jObj.clear();
            }

            int j = 0;

            while (javaKeys != null && j < javaKeys.length)
            {
                map.put(javaKeys[j], javaValues[j]);
                j++;
            }
        }
    }

    // ========== Keep relationship knowledge below the line ==========
    public static Object newInstance(Class c, JSONPOJOElement jsonObject)
    {
        return JSONPOJOReader.newInstance(c, jsonObject);
    }

    protected Map<Class, JSONPOJOReader.JsonClassReaderBase> getReaders()
    {
        return reader.readers;
    }

    protected boolean notCustom(Class cls)
    {
        return reader.notCustom.contains(cls);
    }
}
