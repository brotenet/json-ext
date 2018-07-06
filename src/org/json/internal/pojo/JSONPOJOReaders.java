package org.json.internal.pojo;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.exceptions.JSONIOException;


public class JSONPOJOReaders
{
    private JSONPOJOReaders () {}
    
    private static final String DAYS = "(monday|mon|tuesday|tues|tue|wednesday|wed|thursday|thur|thu|friday|fri|saturday|sat|sunday|sun)"; // longer before shorter matters
    private static final String MOS = "(January|Jan|February|Feb|March|Mar|April|Apr|May|June|Jun|July|Jul|August|Aug|September|Sept|Sep|October|Oct|November|Nov|December|Dec)";
    private static final Pattern datePattern1 = Pattern.compile("(\\d{4})[./-](\\d{1,2})[./-](\\d{1,2})");
    private static final Pattern datePattern2 = Pattern.compile("(\\d{1,2})[./-](\\d{1,2})[./-](\\d{4})");
    private static final Pattern datePattern3 = Pattern.compile(MOS + "[ ]*[,]?[ ]*(\\d{1,2})(st|nd|rd|th|)[ ]*[,]?[ ]*(\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern datePattern4 = Pattern.compile("(\\d{1,2})(st|nd|rd|th|)[ ]*[,]?[ ]*" + MOS + "[ ]*[,]?[ ]*(\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern datePattern5 = Pattern.compile("(\\d{4})[ ]*[,]?[ ]*" + MOS + "[ ]*[,]?[ ]*(\\d{1,2})(st|nd|rd|th|)", Pattern.CASE_INSENSITIVE);
    private static final Pattern datePattern6 = Pattern.compile(DAYS + "[ ]+" + MOS + "[ ]+(\\d{1,2})[ ]+(\\d{2}:\\d{2}:\\d{2})[ ]+[A-Z]{1,4}\\s+(\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern timePattern1 = Pattern.compile("(\\d{2})[.:](\\d{2})[.:](\\d{2})[.](\\d{1,10})([+-]\\d{2}[:]?\\d{2}|Z)?");
    private static final Pattern timePattern2 = Pattern.compile("(\\d{2})[.:](\\d{2})[.:](\\d{2})([+-]\\d{2}[:]?\\d{2}|Z)?");
    private static final Pattern timePattern3 = Pattern.compile("(\\d{2})[.:](\\d{2})([+-]\\d{2}[:]?\\d{2}|Z)?");
    private static final Pattern dayPattern = Pattern.compile(DAYS, Pattern.CASE_INSENSITIVE);
    private static final Map<String, String> months = new LinkedHashMap<String, String>();

    static
    {
        // Month name to number map
        months.put("jan", "1");
        months.put("january", "1");
        months.put("feb", "2");
        months.put("february", "2");
        months.put("mar", "3");
        months.put("march", "3");
        months.put("apr", "4");
        months.put("april", "4");
        months.put("may", "5");
        months.put("jun", "6");
        months.put("june", "6");
        months.put("jul", "7");
        months.put("july", "7");
        months.put("aug", "8");
        months.put("august", "8");
        months.put("sep", "9");
        months.put("sept", "9");
        months.put("september", "9");
        months.put("oct", "10");
        months.put("october", "10");
        months.put("nov", "11");
        months.put("november", "11");
        months.put("dec", "12");
        months.put("december", "12");
    }

    public static class TimeZoneReader implements JSONPOJOReader.JsonClassReaderEx
    {
        public Object read(Object o, Deque<JSONPOJOElement<String, Object>> stack, Map<String, Object> args)
        {
            JSONPOJOElement jObj = (JSONPOJOElement)o;
            Object zone = jObj.get("zone");
            if (zone == null)
            {
                throw new JSONIOException("java.util.TimeZone must specify 'zone' field");
            }
            jObj.target = TimeZone.getTimeZone((String) zone);
            return jObj.target;
        }
    }

    public static class LocaleReader implements JSONPOJOReader.JsonClassReaderEx
    {
        public Object read(Object o, Deque<JSONPOJOElement<String, Object>> stack, Map<String, Object> args)
        {
            JSONPOJOElement jObj = (JSONPOJOElement) o;
            Object language = jObj.get("language");
            if (language == null)
            {
                throw new JSONIOException("java.util.Locale must specify 'language' field");
            }
            Object country = jObj.get("country");
            Object variant = jObj.get("variant");
            if (country == null)
            {
                jObj.target = new Locale((String) language);
                return jObj.target;
            }
            if (variant == null)
            {
                jObj.target = new Locale((String) language, (String) country);
                return jObj.target;
            }

            jObj.target = new Locale((String) language, (String) country, (String) variant);
            return jObj.target;
        }
    }

    public static class CalendarReader implements JSONPOJOReader.JsonClassReaderEx
    {
        public Object read(Object o, Deque<JSONPOJOElement<String, Object>> stack, Map<String, Object> args)
        {
            String time = null;
            try
            {
                JSONPOJOElement jObj = (JSONPOJOElement) o;
                time = (String) jObj.get("time");
                if (time == null)
                {
                    throw new JSONIOException("Calendar missing 'time' field");
                }
                Date date = JSONPOJOMetaUtils.dateFormat.get().parse(time);
                Class c;
                if (jObj.getTarget() != null)
                {
                    c = jObj.getTarget().getClass();
                }
                else
                {
                    Object type = jObj.type;
                    c = classForName((String) type, (ClassLoader)args.get(JSONPOJOReader.CLASSLOADER));
                }

                Calendar calendar = (Calendar) newInstance(c, jObj);
                calendar.setTime(date);
                jObj.setTarget(calendar);
                String zone = (String) jObj.get("zone");
                if (zone != null)
                {
                    calendar.setTimeZone(TimeZone.getTimeZone(zone));
                }
                return calendar;
            }
            catch(Exception e)
            {
                throw new JSONIOException("Failed to parse calendar, time: " + time);
            }
        }
    }

    public static class DateReader implements JSONPOJOReader.JsonClassReaderEx
    {
        public Object read(Object o, Deque<JSONPOJOElement<String, Object>> stack, Map<String, Object> args)
        {
            if (o instanceof Long)
            {
                return new Date((Long) o);
            }
            else if (o instanceof String)
            {
                return parseDate((String) o);
            }
            else if (o instanceof JSONPOJOElement)
            {
                JSONPOJOElement jObj = (JSONPOJOElement) o;
                Object val = jObj.get("value");
                if (val instanceof Long)
                {
                    return new Date((Long) val);
                }
                else if (val instanceof String)
                {
                    return parseDate((String) val);
                }
                throw new JSONIOException("Unable to parse date: " + o);
            }
            else
            {
                throw new JSONIOException("Unable to parse date, encountered unknown object: " + o);
            }
        }

        static Date parseDate(String dateStr)
        {
            dateStr = dateStr.trim();
            if (dateStr.isEmpty())
            {
                return null;
            }

            // Determine which date pattern (Matcher) to use
            Matcher matcher = datePattern1.matcher(dateStr);

            String year, month = null, day, mon = null, remains;

            if (matcher.find())
            {
                year = matcher.group(1);
                month = matcher.group(2);
                day = matcher.group(3);
                remains = matcher.replaceFirst("");
            }
            else
            {
                matcher = datePattern2.matcher(dateStr);
                if (matcher.find())
                {
                    month = matcher.group(1);
                    day = matcher.group(2);
                    year = matcher.group(3);
                    remains = matcher.replaceFirst("");
                }
                else
                {
                    matcher = datePattern3.matcher(dateStr);
                    if (matcher.find())
                    {
                        mon = matcher.group(1);
                        day = matcher.group(2);
                        year = matcher.group(4);
                        remains = matcher.replaceFirst("");
                    }
                    else
                    {
                        matcher = datePattern4.matcher(dateStr);
                        if (matcher.find())
                        {
                            day = matcher.group(1);
                            mon = matcher.group(3);
                            year = matcher.group(4);
                            remains = matcher.replaceFirst("");
                        }
                        else
                        {
                            matcher = datePattern5.matcher(dateStr);
                            if (matcher.find())
                            {
                                year = matcher.group(1);
                                mon = matcher.group(2);
                                day = matcher.group(3);
                                remains = matcher.replaceFirst("");
                            }
                            else
                            {
                                matcher = datePattern6.matcher(dateStr);
                                if (!matcher.find())
                                {
                                    throw new JSONIOException("Unable to parse: " + dateStr);
                                }
                                year = matcher.group(5);
                                mon = matcher.group(2);
                                day = matcher.group(3);
                                remains = matcher.group(4);
                            }
                        }
                    }
                }
            }

            if (mon != null)
            {   // Month will always be in Map, because regex forces this.
                month = months.get(mon.trim().toLowerCase());
            }

            // Determine which date pattern (Matcher) to use
            String hour = null, min = null, sec = "00", milli = "0", tz = null;
            remains = remains.trim();
            matcher = timePattern1.matcher(remains);
            if (matcher.find())
            {
                hour = matcher.group(1);
                min = matcher.group(2);
                sec = matcher.group(3);
                milli = matcher.group(4);
                if (matcher.groupCount() > 4)
                {
                    tz = matcher.group(5);
                }
            }
            else
            {
                matcher = timePattern2.matcher(remains);
                if (matcher.find())
                {
                    hour = matcher.group(1);
                    min = matcher.group(2);
                    sec = matcher.group(3);
                    if (matcher.groupCount() > 3)
                    {
                        tz = matcher.group(4);
                    }
                }
                else
                {
                    matcher = timePattern3.matcher(remains);
                    if (matcher.find())
                    {
                        hour = matcher.group(1);
                        min = matcher.group(2);
                        if (matcher.groupCount() > 2)
                        {
                            tz = matcher.group(3);
                        }
                    }
                    else
                    {
                        matcher = null;
                    }
                }
            }

            if (matcher != null)
            {
                remains = matcher.replaceFirst("");
            }

            // Clear out day of week (mon, tue, wed, ...)
            if (remains != null && remains.length() > 0)
            {
                Matcher dayMatcher = dayPattern.matcher(remains);
                if (dayMatcher.find())
                {
                    remains = dayMatcher.replaceFirst("").trim();
                }
            }
            if (remains != null && remains.length() > 0)
            {
                remains = remains.trim();
                if (!remains.equals(",") && (!remains.equals("T")))
                {
                    throw new JSONIOException("Issue parsing data/time, other characters present: " + remains);
                }
            }

            Calendar c = Calendar.getInstance();
            c.clear();
            if (tz != null)
            {
                if ("z".equalsIgnoreCase(tz))
                {
                    c.setTimeZone(TimeZone.getTimeZone("GMT"));
                }
                else
                {
                    c.setTimeZone(TimeZone.getTimeZone("GMT" + tz));
                }
            }

            // Regex prevents these from ever failing to parse
            int y = Integer.parseInt(year);
            int m = Integer.parseInt(month) - 1;    // months are 0-based
            int d = Integer.parseInt(day);

            if (m < 0 || m > 11)
            {
                throw new JSONIOException("Month must be between 1 and 12 inclusive, date: " + dateStr);
            }
            if (d < 1 || d > 31)
            {
                throw new JSONIOException("Day must be between 1 and 31 inclusive, date: " + dateStr);
            }

            if (matcher == null)
            {   // no [valid] time portion
                c.set(y, m, d);
            }
            else
            {
                // Regex prevents these from ever failing to parse.
                int h = Integer.parseInt(hour);
                int mn = Integer.parseInt(min);
                int s = Integer.parseInt(sec);
                int ms = Integer.parseInt(milli);

                if (h > 23)
                {
                    throw new JSONIOException("Hour must be between 0 and 23 inclusive, time: " + dateStr);
                }
                if (mn > 59)
                {
                    throw new JSONIOException("Minute must be between 0 and 59 inclusive, time: " + dateStr);
                }
                if (s > 59)
                {
                    throw new JSONIOException("Second must be between 0 and 59 inclusive, time: " + dateStr);
                }

                // regex enforces millis to number
                c.set(y, m, d, h, mn, s);
                c.set(Calendar.MILLISECOND, ms);
            }
            return c.getTime();
        }
    }

    public static class SqlDateReader extends DateReader
    {
        public Object read(Object o, Deque<JSONPOJOElement<String, Object>> stack, Map<String, Object> args)
        {
            return new java.sql.Date(((Date) super.read(o, stack, args)).getTime());
        }
    }

    public static class StringReader implements JSONPOJOReader.JsonClassReaderEx
    {
        public Object read(Object o, Deque<JSONPOJOElement<String, Object>> stack, Map<String, Object> args)
        {
            if (o instanceof String)
            {
                return o;
            }

            if (JSONPOJOMetaUtils.isPrimitive(o.getClass()))
            {
                return o.toString();
            }

            JSONPOJOElement jObj = (JSONPOJOElement) o;
            if (jObj.containsKey("value"))
            {
                jObj.target = jObj.get("value");
                return jObj.target;
            }
            throw new JSONIOException("String missing 'value' field");
        }
    }

    public static class ClassReader implements JSONPOJOReader.JsonClassReaderEx
    {
        public Object read(Object o, Deque<JSONPOJOElement<String, Object>> stack, Map<String, Object> args)
        {
            if (o instanceof String)
            {
                return classForName((String) o, (ClassLoader)args.get(JSONPOJOReader.CLASSLOADER));
            }

            JSONPOJOElement jObj = (JSONPOJOElement) o;
            if (jObj.containsKey("value"))
            {
                jObj.target = classForName((String) jObj.get("value"), (ClassLoader)args.get(JSONPOJOReader.CLASSLOADER));
                return jObj.target;
            }
            throw new JSONIOException("Class missing 'value' field");
        }
    }

    public static class AtomicBooleanReader implements JSONPOJOReader.JsonClassReaderEx
    {
        public Object read(Object o, Deque<JSONPOJOElement<String, Object>> stack, Map<String, Object> args)
        {
            Object value = o;
            value = getValueFromJsonObject(o, value, "AtomicBoolean");

            if (value instanceof String)
            {
                String state = (String) value;
                if ("".equals(state.trim()))
                {   // special case
                    return null;
                }
                return new AtomicBoolean("true".equalsIgnoreCase(state));
            }
            else if (value instanceof Boolean)
            {
                return new AtomicBoolean((Boolean) value);
            }
            else if (value instanceof Number && !(value instanceof Double) && !(value instanceof Float))
            {
                return new AtomicBoolean(((Number)value).longValue() != 0);
            }
            throw new JSONIOException("Unknown value in JSON assigned to AtomicBoolean, value type = " + value.getClass().getName());
        }
    }

    public static class AtomicIntegerReader implements JSONPOJOReader.JsonClassReaderEx
    {
        public Object read(Object o, Deque<JSONPOJOElement<String, Object>> stack, Map<String, Object> args)
        {
            Object value = o;
            value = getValueFromJsonObject(o, value, "AtomicInteger");

            if (value instanceof String)
            {
                String num = (String) value;
                if ("".equals(num.trim()))
                {   // special case
                    return null;
                }
                return new AtomicInteger(Integer.parseInt(JSONPOJOMetaUtils.removeLeadingAndTrailingQuotes(num)));
            }
            else if (value instanceof Number && !(value instanceof Double) && !(value instanceof Float))
            {
                return new AtomicInteger(((Number)value).intValue());
            }
            throw new JSONIOException("Unknown value in JSON assigned to AtomicInteger, value type = " + value.getClass().getName());
        }
    }

    public static class AtomicLongReader implements JSONPOJOReader.JsonClassReaderEx
    {
        public Object read(Object o, Deque<JSONPOJOElement<String, Object>> stack, Map<String, Object> args)
        {
            Object value = o;
            value = getValueFromJsonObject(o, value, "AtomicLong");

            if (value instanceof String)
            {
                String num = (String) value;
                if ("".equals(num.trim()))
                {   // special case
                    return null;
                }
                return new AtomicLong(Long.parseLong(JSONPOJOMetaUtils.removeLeadingAndTrailingQuotes(num)));
            }
            else if (value instanceof Number && !(value instanceof Double) && !(value instanceof Float))
            {
                return new AtomicLong(((Number)value).longValue());
            }
            throw new JSONIOException("Unknown value in JSON assigned to AtomicLong, value type = " + value.getClass().getName());
        }
    }

    private static Object getValueFromJsonObject(Object o, Object value, String typeName)
    {
        if (o instanceof JSONPOJOElement)
        {
            JSONPOJOElement jObj = (JSONPOJOElement) o;
            if (jObj.containsKey("value"))
            {
                value = jObj.get("value");
            }
            else
            {
                throw new JSONIOException(typeName + " defined as JSON {} object, missing 'value' field");
            }
        }
        return value;
    }

    public static class BigIntegerReader implements JSONPOJOReader.JsonClassReaderEx
    {
        public Object read(Object o, Deque<JSONPOJOElement<String, Object>> stack, Map<String, Object> args)
        {
            JSONPOJOElement jObj = null;
            Object value = o;
            if (o instanceof JSONPOJOElement)
            {
                jObj = (JSONPOJOElement) o;
                if (jObj.containsKey("value"))
                {
                    value = jObj.get("value");
                }
                else
                {
                    throw new JSONIOException("BigInteger missing 'value' field");
                }
            }

            if (value instanceof JSONPOJOElement)
            {
                JSONPOJOElement valueObj = (JSONPOJOElement)value;
                if ("java.math.BigDecimal".equals(valueObj.type))
                {
                    BigDecimalReader reader = new BigDecimalReader();
                    value = reader.read(value, stack, args);
                }
                else if ("java.math.BigInteger".equals(valueObj.type))
                {
                    value = read(value, stack, args);
                }
                else
                {
                    return bigIntegerFrom(valueObj.get("value"));
                }
            }

            BigInteger x = bigIntegerFrom(value);
            if (jObj != null)
            {
                jObj.target = x;
            }

            return x;
        }
    }

    
    public static BigInteger bigIntegerFrom(Object value)
    {
        if (value == null)
        {
            return null;
        }
        else if (value instanceof BigInteger)
        {
            return (BigInteger) value;
        }
        else if (value instanceof String)
        {
            String s = (String) value;
            if ("".equals(s.trim()))
            {   // Allows "" to be used to assign null to BigInteger field.
                return null;
            }
            try
            {
                return new BigInteger(JSONPOJOMetaUtils.removeLeadingAndTrailingQuotes(s));
            }
            catch (Exception e)
            {
                throw new JSONIOException("Could not parse '" + value + "' as BigInteger.", e);
            }
        }
        else if (value instanceof BigDecimal)
        {
            BigDecimal bd = (BigDecimal) value;
            return bd.toBigInteger();
        }
        else if (value instanceof Boolean)
        {
            return (Boolean) value ? BigInteger.ONE : BigInteger.ZERO;
        }
        else if (value instanceof Double || value instanceof Float)
        {
            return new BigDecimal(((Number)value).doubleValue()).toBigInteger();
        }
        else if (value instanceof Long || value instanceof Integer ||
                value instanceof Short || value instanceof Byte)
        {
            return new BigInteger(value.toString());
        }
        throw new JSONIOException("Could not convert value: " + value.toString() + " to BigInteger.");
    }

    public static class BigDecimalReader implements JSONPOJOReader.JsonClassReaderEx
    {
        public Object read(Object o, Deque<JSONPOJOElement<String, Object>> stack, Map<String, Object> args)
        {
            JSONPOJOElement jObj = null;
            Object value = o;
            if (o instanceof JSONPOJOElement)
            {
                jObj = (JSONPOJOElement) o;
                if (jObj.containsKey("value"))
                {
                    value = jObj.get("value");
                }
                else
                {
                    throw new JSONIOException("BigDecimal missing 'value' field");
                }
            }

            if (value instanceof JSONPOJOElement)
            {
                JSONPOJOElement valueObj = (JSONPOJOElement)value;
                if ("java.math.BigInteger".equals(valueObj.type))
                {
                    BigIntegerReader reader = new BigIntegerReader();
                    value = reader.read(value, stack, args);
                }
                else if ("java.math.BigDecimal".equals(valueObj.type))
                {
                    value = read(value, stack, args);
                }
                else
                {
                    return bigDecimalFrom(valueObj.get("value"));
                }
            }

            BigDecimal x = bigDecimalFrom(value);
            if (jObj != null)
            {
                jObj.target = x;
            }
            return x;
        }
    }

    
    public static BigDecimal bigDecimalFrom(Object value)
    {
        if (value == null)
        {
            return null;
        }
        else if (value instanceof BigDecimal)
        {
            return (BigDecimal) value;
        }
        else if (value instanceof String)
        {
            String s = (String) value;
            if ("".equals(s.trim()))
            {
                return null;
            }
            try
            {
                return new BigDecimal(JSONPOJOMetaUtils.removeLeadingAndTrailingQuotes(s));
            }
            catch (Exception e)
            {
                throw new JSONIOException("Could not parse '" + s + "' as BigDecimal.", e);
            }
        }
        else if (value instanceof BigInteger)
        {
            return new BigDecimal((BigInteger) value);
        }
        else if (value instanceof Boolean)
        {
            return (Boolean) value ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        else if (value instanceof Long || value instanceof Integer || value instanceof Double ||
                value instanceof Short || value instanceof Byte || value instanceof Float)
        {
            return new BigDecimal(value.toString());
        }
        throw new JSONIOException("Could not convert value: " + value.toString() + " to BigInteger.");
    }

    public static class StringBuilderReader implements JSONPOJOReader.JsonClassReaderEx
    {
        public Object read(Object o, Deque<JSONPOJOElement<String, Object>> stack, Map<String, Object> args)
        {
            if (o instanceof String)
            {
                return new StringBuilder((String) o);
            }

            JSONPOJOElement jObj = (JSONPOJOElement) o;
            if (jObj.containsKey("value"))
            {
                jObj.target = new StringBuilder((String) jObj.get("value"));
                return jObj.target;
            }
            throw new JSONIOException("StringBuilder missing 'value' field");
        }
    }

    public static class StringBufferReader implements JSONPOJOReader.JsonClassReaderEx
    {
        public Object read(Object o, Deque<JSONPOJOElement<String, Object>> stack, Map<String, Object> args)
        {
            if (o instanceof String)
            {
                return new StringBuffer((String) o);
            }

            JSONPOJOElement jObj = (JSONPOJOElement) o;
            if (jObj.containsKey("value"))
            {
                jObj.target = new StringBuffer((String) jObj.get("value"));
                return jObj.target;
            }
            throw new JSONIOException("StringBuffer missing 'value' field");
        }
    }

    public static class TimestampReader implements JSONPOJOReader.JsonClassReaderEx
    {
        public Object read(Object o, Deque<JSONPOJOElement<String, Object>> stack, Map<String, Object> args)
        {
            JSONPOJOElement jObj = (JSONPOJOElement) o;
            Object time = jObj.get("time");
            if (time == null)
            {
                throw new JSONIOException("java.sql.Timestamp must specify 'time' field");
            }
            Object nanos = jObj.get("nanos");
            if (nanos == null)
            {
                jObj.target = new Timestamp(Long.valueOf((String) time));
                return jObj.target;
            }

            Timestamp tstamp = new Timestamp(Long.valueOf((String) time));
            tstamp.setNanos(Integer.valueOf((String) nanos));
            jObj.target = tstamp;
            return jObj.target;
        }
    }

    // ========== Maintain dependency knowledge in once place, down here =========
    static Class classForName(String name, ClassLoader classLoader)
    {
        return JSONPOJOMetaUtils.classForName(name, classLoader);
    }

    static Object newInstance(Class c, JSONPOJOElement jsonObject)
    {
        return JSONPOJOReader.newInstance(c, jsonObject);
    }
}
