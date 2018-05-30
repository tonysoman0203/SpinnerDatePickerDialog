import android.app.DatePickerDialog
import android.content.Context
import android.os.Build
import android.support.annotation.RequiresApi
import android.util.AttributeSet
import android.view.View
import android.widget.CalendarView
import android.widget.DatePicker
import android.widget.LinearLayout
import java.lang.reflect.Field


@RequiresApi(Build.VERSION_CODES.N)
/**
 * Created by tonyso on 26/2/2018.
 */
class SpinnerDatePickerDialog @JvmOverloads constructor(context: Context,
                                                        listener: OnDateSetListener,
                                                        themeResId: Int = 0,
                                                        mYear: Int,
                                                        mMonth: Int,
                                                        mDay: Int
) : DatePickerDialog(context, themeResId, listener, mYear, mMonth, mDay) {
    init {
        fixSpinner(context, mYear, mMonth, mDay)
    }

    private fun fixSpinner(context: Context, year: Int, month: Int, dayOfMonth: Int) {
        // The spinner vs not distinction probably started in lollipop but applying this
        // for versions < nougat leads to a crash trying to get DatePickerSpinnerDelegate
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                // Get the theme's android:datePickerMode
                val MODE_SPINNER = 2
                val styleableClass = Class.forName("com.android.internal.R\$styleable")
                val datePickerStyleableField = styleableClass.getField("DatePicker")
                val datePickerStyleable = datePickerStyleableField.get(
                        null) as IntArray
                val a = context.obtainStyledAttributes(null, datePickerStyleable,
                        android.R.attr.datePickerStyle, 0)
                val datePickerModeStyleableField = styleableClass.getField("DatePicker_datePickerMode")
                val datePickerModeStyleable = datePickerModeStyleableField.getInt(null)
                val mode = a.getInt(datePickerModeStyleable, MODE_SPINNER)
                a.recycle()

                if (mode == MODE_SPINNER) {
                    val datePicker = findField(DatePickerDialog::class.java,
                            DatePicker::class.java, "mDatePicker")!!.get(this) as DatePicker
                    val delegateClass = Class.forName("android.widget.DatePicker\$DatePickerDelegate")
                    val delegateField = findField(DatePicker::class.java, delegateClass, "mDelegate")
                    var delegate = delegateField!!.get(datePicker)

                    val spinnerDelegateClass = Class.forName("android.widget.DatePickerSpinnerDelegate")

                    // In 7.0 Nougat for some reason the datePickerMode is ignored and the
                    // delegate is DatePickerCalendarDelegate
                    if (delegate.javaClass != spinnerDelegateClass) {
                        delegateField.set(datePicker, null) // throw out the DatePickerCalendarDelegate!
                        datePicker.removeAllViews() // remove the DatePickerCalendarDelegate views

                        val spinnerDelegateConstructor = spinnerDelegateClass
                                .getDeclaredConstructor(DatePicker::class.java, Context::class.java,
                                        AttributeSet::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                        spinnerDelegateConstructor.isAccessible = true

                        // Instantiate a DatePickerSpinnerDelegate
                        delegate = spinnerDelegateConstructor.newInstance(datePicker, context, null, android.R.attr.datePickerStyle, 0)

                        // set the DatePicker.mDelegate to the spinner delegate
                        delegateField.set(datePicker, delegate)

                        // Set up the DatePicker again, with the DatePickerSpinnerDelegate
                        datePicker.updateDate(year, month, dayOfMonth)

                        // Fix the issue of floating left of the datepicker
                        for (i in 0..datePicker.childCount) {
                            val view = datePicker.getChildAt(i)
                            if (view is LinearLayout) {
                                (0..view.childCount)
                                        .asSequence()
                                        .filter { view.getChildAt(it) is CalendarView }
                                        .forEach { view.getChildAt(it).visibility = View.GONE }
                            }
                            continue
                        }

                    }
                }
            } catch (e: Exception) {
                throw RuntimeException(e)
            }

        }
    }

    private fun findField(objectClass: Class<*>, fieldClass: Class<*>, expectedName: String): Field? {
        try {
            val field = objectClass.getDeclaredField(expectedName)
            field.isAccessible = true
            return field
        } catch (e: NoSuchFieldException) {

        }

        // search for it if it wasn't found under the expected ivar name
        for (searchField in objectClass.declaredFields) {
            if (searchField.type == fieldClass) {
                searchField.isAccessible = true
                return searchField
            }
        }
        return null
    }
}