package org.json.internal.pojo;

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.json.exceptions.JSONIOException;


public class JSONPOJOReader implements Closeable
{
    
    public static final String CUSTOM_READER_MAP = "CUSTOM_READERS";
    
    public static final String NOT_CUSTOM_READER_MAP = "NOT_CUSTOM_READERS";
    
    public static final String USE_MAPS = "USE_MAPS";
    
    public static final String UNKNOWN_OBJECT = "UNKNOWN_OBJECT";
    
    public static final String FAIL_ON_UNKNOWN_TYPE = "FAIL_ON_UNKNOWN_TYPE";
    
    public static final String JSON_READER = "JSON_READER";
    
    public static final String OBJECT_RESOLVER = "OBJECT_RESOLVER";
    
    public static final String TYPE_NAME_MAP = "TYPE_NAME_MAP";
    
    public static final String MISSING_FIELD_HANDLER = "MISSING_FIELD_HANDLER";
    
    public static final String CLASSLOADER = "CLASSLOADER";
    
    static final String TYPE_NAME_MAP_REVERSE = "TYPE_NAME_MAP_REVERSE";

    private static Map<Class, JsonClassReaderBase> BASE_READERS;
    protected final Map<Class, JsonClassReaderBase> readers = new HashMap<Class, JsonClassReaderBase>(BASE_READERS);
    protected MissingFieldHandler missingFieldHandler;
    protected final Set<Class> notCustom = new HashSet<Class>();
    private static final Map<String, Factory> factory = new ConcurrentHashMap<String, Factory>();
    private final Map<Long, JSONPOJOElement> objsRead = new HashMap<Long, JSONPOJOElement>();
    private final JSONPOJOFastPushbackReader input;
    
    private final Map<String, Object> args = new HashMap<String, Object>();

    static
    {
        Factory colFactory = new CollectionFactory();
        assignInstantiator(Collection.class, colFactory);
        assignInstantiator(List.class, colFactory);
        assignInstantiator(Set.class, colFactory);
        assignInstantiator(SortedSet.class, colFactory);

        Factory mapFactory = new MapFactory();
        assignInstantiator(Map.class, mapFactory);
        assignInstantiator(SortedMap.class, mapFactory);

        Map<Class, JsonClassReaderBase> temp = new HashMap<Class, JsonClassReaderBase>();
        temp.put(String.class, new JSONPOJOReaders.StringReader());
        temp.put(Date.class, new JSONPOJOReaders.DateReader());
        temp.put(AtomicBoolean.class, new JSONPOJOReaders.AtomicBooleanReader());
        temp.put(AtomicInteger.class, new JSONPOJOReaders.AtomicIntegerReader());
        temp.put(AtomicLong.class, new JSONPOJOReaders.AtomicLongReader());
        temp.put(BigInteger.class, new JSONPOJOReaders.BigIntegerReader());
        temp.put(BigDecimal.class, new JSONPOJOReaders.BigDecimalReader());
        temp.put(java.sql.Date.class, new JSONPOJOReaders.SqlDateReader());
        temp.put(Timestamp.class, new JSONPOJOReaders.TimestampReader());
        temp.put(Calendar.class, new JSONPOJOReaders.CalendarReader());
        temp.put(TimeZone.class, new JSONPOJOReaders.TimeZoneReader());
        temp.put(Locale.class, new JSONPOJOReaders.LocaleReader());
        temp.put(Class.class, new JSONPOJOReaders.ClassReader());
        temp.put(StringBuilder.class, new JSONPOJOReaders.StringBuilderReader());
        temp.put(StringBuffer.class, new JSONPOJOReaders.StringBufferReader());
        BASE_READERS = temp;
    }

    
    public interface Factory
    {
    }

    
    public interface ClassFactory extends Factory
    {
        Object newInstance(Class c);
    }

    
    public interface ClassFactoryEx extends Factory
    {
        Object newInstance(Class c, Map args);
    }

    
    public interface MissingFieldHandler
    {
        
        
        void fieldMissing(Object object, String fieldName, Object value);

    }

    
    public interface JsonClassReaderBase  { }

    
    public interface JsonClassReader extends JsonClassReaderBase
    {
        
        Object read(Object jOb, Deque<JSONPOJOElement<String, Object>> stack);
    }

    
    public interface JsonClassReaderEx extends JsonClassReaderBase
    {
        
        Object read(Object jOb, Deque<JSONPOJOElement<String, Object>> stack, Map<String, Object> args);

        
        class Support
        {
            
            public static JSONPOJOReader getReader(Map<String, Object> args)
            {
                return (JSONPOJOReader) args.get(JSON_READER);
            }
        }
    }

    
    public static class CollectionFactory implements ClassFactory
    {
        public Object newInstance(Class c)
        {
            if (List.class.isAssignableFrom(c))
            {
                return new ArrayList();
            }
            else if (SortedSet.class.isAssignableFrom(c))
            {
                return new TreeSet();
            }
            else if (Set.class.isAssignableFrom(c))
            {
                return new LinkedHashSet();
            }
            else if (Collection.class.isAssignableFrom(c))
            {
                return new ArrayList();
            }
            throw new JSONIOException("CollectionFactory handed Class for which it was not expecting: " + c.getName());
        }
    }

    
    public static class MapFactory implements ClassFactory
    {
        
        public Object newInstance(Class c)
        {
            if (SortedMap.class.isAssignableFrom(c))
            {
                return new TreeMap();
            }
            else if (Map.class.isAssignableFrom(c))
            {
                return new LinkedHashMap();
            }
            throw new JSONIOException("MapFactory handed Class for which it was not expecting: " + c.getName());
        }
    }

    
    public static void assignInstantiator(String n, Factory f)
    {
        factory.put(n, f);
    }

    
    public static void assignInstantiator(Class c, Factory f)
    {
        assignInstantiator(c.getName(), f);
    }

    
    public void addReader(Class c, JsonClassReaderBase reader)
    {
        readers.put(c, reader);
    }

    
    public static void addReaderPermanent(Class c, JsonClassReaderBase reader)
    {
        BASE_READERS.put(c, reader);
    }

    
    public void addNotCustomReader(Class c)
    {
        notCustom.add(c);
    }

    MissingFieldHandler getMissingFieldHandler()
    {
        return missingFieldHandler;
    }

    public void setMissingFieldHandler(MissingFieldHandler handler)
    {
        missingFieldHandler = handler;
    }

    
    public Map<String, Object> getArgs()
    {
        return args;
    }

    
    public static Object jsonToJava(String json)
    {
        return jsonToJava(json, null);
    }

    
    public static Object jsonToJava(String json, Map<String, Object> optionalArgs)
    {
        if (optionalArgs == null)
        {
            optionalArgs = new HashMap<String, Object>();
            optionalArgs.put(USE_MAPS, false);
        }
        if (!optionalArgs.containsKey(USE_MAPS))
        {
            optionalArgs.put(USE_MAPS, false);
        }
        JSONPOJOReader jr = new JSONPOJOReader(json, optionalArgs);
        Object obj = jr.readObject();
        jr.close();
        return obj;
    }

    
    public static Object jsonToJava(InputStream inputStream, Map<String, Object> optionalArgs)
    {
        if (optionalArgs == null)
        {
            optionalArgs = new HashMap<String, Object>();
            optionalArgs.put(USE_MAPS, false);
        }
        if (!optionalArgs.containsKey(USE_MAPS))
        {
            optionalArgs.put(USE_MAPS, false);
        }
        JSONPOJOReader jr = new JSONPOJOReader(inputStream, optionalArgs);
        Object obj = jr.readObject();
        jr.close();
        return obj;
    }

    
    public static Map jsonToMaps(String json)
    {
        return jsonToMaps(json, null);
    }

    
    public static Map jsonToMaps(String json, Map<String, Object> optionalArgs)
    {
        try
        {
            if (optionalArgs == null)
            {
                optionalArgs = new HashMap<String, Object>();
            }
            optionalArgs.put(USE_MAPS, true);
            ByteArrayInputStream ba = new ByteArrayInputStream(json.getBytes("UTF-8"));
            JSONPOJOReader jr = new JSONPOJOReader(ba, optionalArgs);
            Object ret = jr.readObject();
            jr.close();

            return adjustOutputMap(ret);
        }
        catch (UnsupportedEncodingException e)
        {
            throw new JSONIOException("Could not convert JSON to Maps because your JVM does not support UTF-8", e);
        }
    }

    
    public static Map jsonToMaps(InputStream inputStream, Map<String, Object> optionalArgs)
    {
        if (optionalArgs == null)
        {
            optionalArgs = new HashMap<String, Object>();
        }
        optionalArgs.put(USE_MAPS, true);
        JSONPOJOReader jr = new JSONPOJOReader(inputStream, optionalArgs);
        Object ret = jr.readObject();
        jr.close();

        return adjustOutputMap(ret);
    }

    private static Map adjustOutputMap(Object ret)
    {
        if (ret instanceof Map)
        {
            return (Map) ret;
        }

        if (ret != null && ret.getClass().isArray())
        {
            JSONPOJOElement<String, Object> retMap = new JSONPOJOElement<String, Object>();
            retMap.put("@items", ret);
            return retMap;
        }
        JSONPOJOElement<String, Object> retMap = new JSONPOJOElement<String, Object>();
        retMap.put("@items", new Object[]{ret});
        return retMap;
    }

    public JSONPOJOReader()
    {
        input = null;
        getArgs().put(USE_MAPS, false);
        getArgs().put(CLASSLOADER, JSONPOJOReader.class.getClassLoader());
    }

    public JSONPOJOReader(InputStream inp)
    {
        this(inp, false);
    }

    
    public JSONPOJOReader(Map<String, Object> optionalArgs)
    {
        this(new ByteArrayInputStream(new byte[]{}), optionalArgs);
    }

    // This method is needed to get around the fact that 'this()' has to be the first method of a constructor.
    static Map makeArgMap(Map<String, Object> args, boolean useMaps)
    {
        args.put(USE_MAPS, useMaps);
        return args;
    }

    public JSONPOJOReader(InputStream inp, boolean useMaps)
    {
        this(inp, makeArgMap(new HashMap<String, Object>(), useMaps));
    }

    public JSONPOJOReader(InputStream inp, Map<String, Object> optionalArgs)
    {
        initializeFromArgs(optionalArgs);

        try
        {
            input = new JSONPOJOFastPushbackBufferedReader(new InputStreamReader(inp, "UTF-8"));
        }
        catch (UnsupportedEncodingException e)
        {
            throw new JSONIOException("Your JVM does not support UTF-8.  Get a better JVM.", e);
        }
    }

    public JSONPOJOReader(String inp, Map<String, Object> optionalArgs)
    {
        initializeFromArgs(optionalArgs);
        try
        {
            byte[] bytes = inp.getBytes("UTF-8");
            input = new JSONPOJOFastPushbackBufferedReader(new InputStreamReader(new ByteArrayInputStream(bytes), "UTF-8"));
        }
        catch (UnsupportedEncodingException e)
        {
            throw new JSONIOException("Could not convert JSON to Maps because your JVM does not support UTF-8", e);
        }
    }

    public JSONPOJOReader(byte[] inp, Map<String, Object> optionalArgs)
    {
        initializeFromArgs(optionalArgs);
        try
        {
            input = new JSONPOJOFastPushbackBufferedReader(new InputStreamReader(new ByteArrayInputStream(inp), "UTF-8"));
        }
        catch (UnsupportedEncodingException e)
        {
            throw new JSONIOException("Could not convert JSON to Maps because your JVM does not support UTF-8", e);
        }
    }

    private void initializeFromArgs(Map<String, Object> optionalArgs)
    {
        if (optionalArgs == null)
        {
            optionalArgs = new HashMap();
        }
        Map<String, Object> args = getArgs();
        args.putAll(optionalArgs);
        args.put(JSON_READER, this);
        if (!args.containsKey(CLASSLOADER))
        {
            args.put(CLASSLOADER, JSONPOJOReader.class.getClassLoader());
        }
        Map<String, String> typeNames = (Map<String, String>) args.get(TYPE_NAME_MAP);

        if (typeNames != null)
        { // Reverse the Map (this allows the users to only have a Map from type to short-hand name,
            // and not keep a 2nd map from short-hand name to type.
            Map<String, String> typeNameMap = new HashMap<String, String>();
            for (Map.Entry<String, String> entry : typeNames.entrySet())
            {
                typeNameMap.put(entry.getValue(), entry.getKey());
            }
            args.put(TYPE_NAME_MAP_REVERSE, typeNameMap); // replace with our reversed Map.
        }

        setMissingFieldHandler((MissingFieldHandler) args.get(MISSING_FIELD_HANDLER));

        Map<Class, JsonClassReaderBase> customReaders = (Map<Class, JsonClassReaderBase>) args.get(CUSTOM_READER_MAP);
        if (customReaders != null)
        {
            for (Map.Entry<Class, JsonClassReaderBase> entry : customReaders.entrySet())
            {
                addReader(entry.getKey(), entry.getValue());
            }
        }

        Iterable<Class> notCustomReaders = (Iterable<Class>) args.get(NOT_CUSTOM_READER_MAP);
        if (notCustomReaders != null)
        {
            for (Class c : notCustomReaders)
            {
                addNotCustomReader(c);
            }
        }
    }

    public Map<Long, JSONPOJOElement> getObjectsRead()
    {
        return objsRead;
    }

    public Object getRefTarget(JSONPOJOElement jObj)
    {
        if (!jObj.isReference())
        {
            return jObj;
        }

        Long id = jObj.getReferenceId();
        JSONPOJOElement target = objsRead.get(id);
        if (target == null)
        {
            throw new IllegalStateException("The JSON input had an @ref to an object that does not exist.");
        }
        return getRefTarget(target);
    }

    
    public Object readObject()
    {
        JSONPOJOParser parser = new JSONPOJOParser(input, objsRead, getArgs());
        JSONPOJOElement<String, Object> root = new JSONPOJOElement();
        Object o;
        try
        {
            o = parser.readValue(root);
            if (o == JSONPOJOParser.EMPTY_OBJECT)
            {
                return new JSONPOJOElement();
            }
        }
        catch (JSONIOException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new JSONIOException("error parsing JSON value", e);
        }

        Object graph;
        if (o instanceof Object[])
        {
            root.setType(Object[].class.getName());
            root.setTarget(o);
            root.put("@items", o);
            graph = convertParsedMapsToJava(root);
        }
        else
        {
            graph = o instanceof JSONPOJOElement ? convertParsedMapsToJava((JSONPOJOElement) o) : o;
        }

        // Allow a complete 'Map' return (Javascript style)
        if (useMaps())
        {
            return o;
        }
        return graph;
    }

    
    public Object jsonObjectsToJava(JSONPOJOElement root)
    {
        getArgs().put(USE_MAPS, false);
        return convertParsedMapsToJava(root);
    }

    protected boolean useMaps()
    {
        return Boolean.TRUE.equals(getArgs().get(USE_MAPS));
    }

    
    ClassLoader getClassLoader()
    {
        return (ClassLoader) args.get(CLASSLOADER);
    }

    
    protected Object convertParsedMapsToJava(JSONPOJOElement root)
    {
        try
        {
            JSONPOJOResolver resolver = useMaps() ? new JSONPOJOMapResolver(this) : new JSONPOJOObjectResolver(this, (ClassLoader)args.get(CLASSLOADER));
            resolver.createJavaObjectInstance(Object.class, root);
            Object graph = resolver.convertMapsToObjects((JSONPOJOElement<String, Object>) root);
            resolver.cleanup();
            readers.clear();
            return graph;
        }
        catch (Exception e)
        {
            try
            {
                close();
            }
            catch (Exception ignored)
            {   // Exception handled in close()
            }
            if (e instanceof JSONIOException)
            {
                throw (JSONIOException)e;
            }
            throw new JSONIOException(getErrorMessage(e.getMessage()), e);
        }
    }

    public static Object newInstance(Class c)
    {
        if (factory.containsKey(c.getName()))
        {
            ClassFactory cf = (ClassFactory) factory.get(c.getName());
            return cf.newInstance(c);
        }
        return JSONPOJOMetaUtils.newInstance(c);
    }

    public static Object newInstance(Class c, JSONPOJOElement jsonObject)
    {
        if (factory.containsKey(c.getName()))
        {
            Factory cf = factory.get(c.getName());
            if (cf instanceof ClassFactoryEx)
            {
                Map args = new HashMap();
                args.put("jsonObj", jsonObject);
                return ((ClassFactoryEx)cf).newInstance(c, args);
            }
            if (cf instanceof ClassFactory)
            {
                return ((ClassFactory)cf).newInstance(c);
            }
            throw new JSONIOException("Unknown instantiator (Factory) class.  Must subclass ClassFactoryEx or ClassFactory, found: " + cf.getClass().getName());
        }
        return JSONPOJOMetaUtils.newInstance(c);
    }

    public void close()
    {
        try
        {
            if (input != null)
            {
                input.close();
            }
        }
        catch (Exception e)
        {
            throw new JSONIOException("Unable to close input", e);
        }
    }

    private String getErrorMessage(String msg)
    {
        if (input != null)
        {
            return msg + "\nLast read: " + input.getLastSnippet() + "\nline: " + input.getLine() + ", col: " + input.getCol();
        }
        return msg;
    }
}
