package android.support.v4.view;

import android.content.Context;
import android.text.method.SingleLineTransformationMethod;
import android.view.View;
import android.widget.TextView;
import java.util.Locale;

class PagerTitleStripIcs {

    private static class SingleLineAllCapsTransform extends SingleLineTransformationMethod {
        private Locale mLocale;

        public SingleLineAllCapsTransform(Context context) {
            this.mLocale = context.getResources().getConfiguration().locale;
        }

        public CharSequence getTransformation(CharSequence source, View view) {
            source = super.getTransformation(source, view);
            return source != null ? source.toString().toUpperCase(this.mLocale) : null;
        }
    }

    public static void setSingleLineAllCaps(TextView text) {
        text.setTransformationMethod(new SingleLineAllCapsTransform(text.getContext()));
    }
}
