package org.json.internal.pojo;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Map;


public class JSONPOJOMapResolver extends JSONPOJOResolver
{
    protected JSONPOJOMapResolver(JSONPOJOReader reader)
    {
        super(reader);
    }

    protected Object readIfMatching(Object o, Class compType, Deque<JSONPOJOElement<String, Object>> stack)
    {
        // No custom reader support for maps
        return null;
    }

    
    public void traverseFields(final Deque<JSONPOJOElement<String, Object>> stack, final JSONPOJOElement<String, Object> jsonObj)
    {
        final Object target = jsonObj.target;
        for (Map.Entry<String, Object> e : jsonObj.entrySet())
        {
            final String fieldName = e.getKey();
            final Field field = (target != null) ? JSONPOJOMetaUtils.getField(target.getClass(), fieldName) : null;
            final Object rhs = e.getValue();

            if (rhs == null)
            {
                jsonObj.put(fieldName, null);
            }
            else if (rhs == JSONPOJOParser.EMPTY_OBJECT)
            {
                jsonObj.put(fieldName, new JSONPOJOElement());
            }
            else if (rhs.getClass().isArray())
            {   // RHS is an array
                // Trace the contents of the array (so references inside the array and into the array work)
                JSONPOJOElement<String, Object> jsonArray = new JSONPOJOElement<String, Object>();
                jsonArray.put("@items", rhs);
                stack.addFirst(jsonArray);

                // Assign the array directly to the Map key (field name)
                jsonObj.put(fieldName, rhs);
            }
            else if (rhs instanceof JSONPOJOElement)
            {
                JSONPOJOElement<String, Object> jObj = (JSONPOJOElement) rhs;

                if (field != null && JSONPOJOMetaUtils.isLogicalPrimitive(field.getType()))
                {
                    jObj.put("value", JSONPOJOMetaUtils.convert(field.getType(), jObj.get("value")));
                    continue;
                }
                Long refId = jObj.getReferenceId();

                if (refId != null)
                {    // Correct field references
                    JSONPOJOElement refObject = getReferencedObj(refId);
                    jsonObj.put(fieldName, refObject);    // Update Map-of-Maps reference
                }
                else
                {
                    stack.addFirst(jObj);
                }
            }
            else if (field != null)
            {   // The code below is 'upgrading' the RHS values in the passed in JsonObject Map
                // by using the @type class name (when specified and exists), to coerce the vanilla
                // JSON values into the proper types defined by the class listed in @type.  This is
                // a cool feature of json-io, that even when reading a map-of-maps JSON file, it will
                // improve the final types of values in the maps RHS, to be of the field type that
                // was optionally specified in @type.
                final Class fieldType = field.getType();
                if (JSONPOJOMetaUtils.isPrimitive(fieldType) || BigDecimal.class.equals(fieldType) || BigInteger.class.equals(fieldType) || Date.class.equals(fieldType))
                {
                    jsonObj.put(fieldName, JSONPOJOMetaUtils.convert(fieldType, rhs));
                }
                else if (rhs instanceof String)
                {
                    if (fieldType != String.class && fieldType != StringBuilder.class && fieldType != StringBuffer.class)
                    {
                        if ("".equals(((String)rhs).trim()))
                        {   // Allow "" to null out a non-String field on the inbound JSON
                            jsonObj.put(fieldName, null);
                        }
                    }
                }
            }
        }
        jsonObj.target = null;  // don't waste space (used for typed return, not for Map return)
    }

    
    protected void traverseCollection(final Deque<JSONPOJOElement<String, Object>> stack, final JSONPOJOElement<String, Object> jsonObj)
    {
        final Object[] items = jsonObj.getArray();
        if (items == null || items.length == 0)
        {
            return;
        }

        int idx = 0;
        final List copy = new ArrayList(items.length);

        for (Object element : items)
        {
            if (element == JSONPOJOParser.EMPTY_OBJECT)
            {
                copy.add(new JSONPOJOElement());
                continue;
            }

            copy.add(element);

            if (element instanceof Object[])
            {   // array element inside Collection
                JSONPOJOElement<String, Object> jsonObject = new JSONPOJOElement<String, Object>();
                jsonObject.put("@items", element);
                stack.addFirst(jsonObject);
            }
            else if (element instanceof JSONPOJOElement)
            {
                JSONPOJOElement<String, Object> jsonObject = (JSONPOJOElement<String, Object>) element;
                Long refId = jsonObject.getReferenceId();

                if (refId != null)
                {    // connect reference
                    JSONPOJOElement refObject = getReferencedObj(refId);
                    copy.set(idx, refObject);
                }
                else
                {
                    stack.addFirst(jsonObject);
                }
            }
            idx++;
        }
        jsonObj.target = null;  // don't waste space (used for typed return, not generic Map return)

        for (int i=0; i < items.length; i++)
        {
            items[i] = copy.get(i);
        }
    }

    protected void traverseArray(Deque<JSONPOJOElement<String, Object>> stack, JSONPOJOElement<String, Object> jsonObj)
    {
        traverseCollection(stack, jsonObj);
    }
}
