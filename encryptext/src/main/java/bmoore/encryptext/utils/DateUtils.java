package bmoore.encryptext.utils;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;

/**
 * Created by Benjamin Moore on 11/28/2015.
 */
public class DateUtils {

    private static Calendar calendar;

    static {
        calendar = new GregorianCalendar(Locale.getDefault());
    }

    public static String buildDate()
    {
        String time;

        int hour = calendar.get(Calendar.HOUR);

        if(hour == 0)
            time = "12:";
        else
            time = hour + ":";


        int minute = calendar.get(Calendar.MINUTE);
        if(minute < 10) //apply minute filtering
            time += "0" + minute;
        else
            time += minute;

        if(calendar.get(Calendar.AM_PM) == 0)
            time += " AM";
        else
            time += " PM";

        time += "," + calendar.get(Calendar.MONTH) + ","
                + calendar.get(Calendar.DAY_OF_MONTH) + "," + calendar.get(Calendar.YEAR);

        return time;
    }

    public static String formatDate(String date)
    {
        //Log.i("Adapter", "Formatting date");
        if(date == null)
            return null;

        if("Sending".equals(date) || !date.contains(","))
            return date;

        String[] dateParts = date.split(",");

        if(Integer.decode(dateParts[3]) == calendar.get(Calendar.YEAR))
        {
            if(Integer.decode(dateParts[1]) == calendar.get(Calendar.MONTH) && Integer.decode(dateParts[2])
                    == calendar.get(Calendar.DAY_OF_MONTH))
            {
                //Log.i("Adapter", "Returning " + dateParts[0]);
                return dateParts[0]; //return time of day
            }

            //Log.i("Adapter", "Returning " + findMonth(dateParts[1]) + " " + dateParts[2]);
            return findMonth(dateParts[1]) + " " + dateParts[2]; //return day of month ex: May 17
        }
        //Log.i("Adapter", "Returning " + findMonth(dateParts[1]) + " " + dateParts[2] + ", " + dateParts[3]);
        return findMonth(dateParts[1]) + " " + dateParts[2] + ", " + dateParts[3];
    }

    private static String findMonth(String val)
    {

        if(val.equals("0"))
            return "Jan ";
        else if(val.equals("1"))
            return "Feb ";
        else if(val.equals("2"))
            return "Mar ";
        else if(val.equals("3"))
            return "Apr ";
        else if(val.equals("4"))
            return "May ";
        else if(val.equals("5"))
            return "Jun ";
        else if(val.equals("6"))
            return "Jul ";
        else if(val.equals("7"))
            return "Aug ";
        else if(val.equals("8"))
            return "Sep ";
        else if(val.equals("9"))
            return "Oct ";
        else if(val.equals("10"))
            return "Nov ";
        else
            return "Dec ";
    }
}
