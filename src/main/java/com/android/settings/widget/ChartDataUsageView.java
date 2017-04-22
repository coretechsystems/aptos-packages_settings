package com.android.settings.widget;

import android.content.Context;
import android.content.res.Resources;
import android.net.NetworkPolicy;
import android.net.NetworkStatsHistory;
import android.os.Handler;
import android.os.Message;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.Time;
import android.util.AttributeSet;
import android.view.MotionEvent;
import com.android.settings.R;
import com.android.settings.widget.ChartSweepView.OnSweepListener;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Objects;

public class ChartDataUsageView extends ChartView {
    private ChartNetworkSeriesView mDetailSeries;
    private ChartGridView mGrid;
    private Handler mHandler;
    private NetworkStatsHistory mHistory;
    private long mInspectEnd;
    private long mInspectStart;
    private DataUsageChartListener mListener;
    private ChartNetworkSeriesView mSeries;
    private ChartSweepView mSweepLimit;
    private ChartSweepView mSweepWarning;
    private OnSweepListener mVertListener;
    private long mVertMax;

    public interface DataUsageChartListener {
        void onLimitChanged();

        void onWarningChanged();

        void requestLimitEdit();

        void requestWarningEdit();
    }

    public static class DataAxis implements ChartAxis {
        private static final Object sSpanSize = new Object();
        private static final Object sSpanUnit = new Object();
        private long mMax;
        private long mMin;
        private float mSize;

        public int hashCode() {
            return Objects.hash(new Object[]{Long.valueOf(this.mMin), Long.valueOf(this.mMax), Float.valueOf(this.mSize)});
        }

        public boolean setBounds(long min, long max) {
            if (this.mMin == min && this.mMax == max) {
                return false;
            }
            this.mMin = min;
            this.mMax = max;
            return true;
        }

        public boolean setSize(float size) {
            if (this.mSize == size) {
                return false;
            }
            this.mSize = size;
            return true;
        }

        public float convertToPoint(long value) {
            return (this.mSize * ((float) (value - this.mMin))) / ((float) (this.mMax - this.mMin));
        }

        public long convertToValue(float point) {
            return (long) (((float) this.mMin) + ((((float) (this.mMax - this.mMin)) * point) / this.mSize));
        }

        public long buildLabel(Resources res, SpannableStringBuilder builder, long value) {
            CharSequence unit;
            long unitFactor;
            CharSequence size;
            double resultRounded;
            if (value < 1048576000) {
                unit = res.getText(17039469);
                unitFactor = 1048576;
            } else {
                unit = res.getText(17039470);
                unitFactor = 1073741824;
            }
            double result = ((double) value) / ((double) unitFactor);
            if (result < 10.0d) {
                size = String.format("%.1f", new Object[]{Double.valueOf(result)});
                resultRounded = (double) ((Math.round(10.0d * result) * unitFactor) / 10);
            } else {
                size = String.format("%.0f", new Object[]{Double.valueOf(result)});
                resultRounded = (double) (Math.round(result) * unitFactor);
            }
            ChartDataUsageView.setText(builder, sSpanSize, size, "^1");
            ChartDataUsageView.setText(builder, sSpanUnit, unit, "^2");
            return (long) resultRounded;
        }

        public float[] getTickPoints() {
            long range = this.mMax - this.mMin;
            long tickJump = ChartDataUsageView.roundUpToPowerOfTwo(range / 16);
            float[] tickPoints = new float[((int) (range / tickJump))];
            long value = this.mMin;
            for (int i = 0; i < tickPoints.length; i++) {
                tickPoints[i] = convertToPoint(value);
                value += tickJump;
            }
            return tickPoints;
        }

        public int shouldAdjustAxis(long value) {
            float point = convertToPoint(value);
            if (((double) point) < ((double) this.mSize) * 0.1d) {
                return -1;
            }
            if (((double) point) > ((double) this.mSize) * 0.85d) {
                return 1;
            }
            return 0;
        }
    }

    public static class TimeAxis implements ChartAxis {
        private static final int FIRST_DAY_OF_WEEK = (Calendar.getInstance().getFirstDayOfWeek() - 1);
        private long mMax;
        private long mMin;
        private float mSize;

        public TimeAxis() {
            long currentTime = System.currentTimeMillis();
            setBounds(currentTime - 2592000000L, currentTime);
        }

        public int hashCode() {
            return Objects.hash(new Object[]{Long.valueOf(this.mMin), Long.valueOf(this.mMax), Float.valueOf(this.mSize)});
        }

        public boolean setBounds(long min, long max) {
            if (this.mMin == min && this.mMax == max) {
                return false;
            }
            this.mMin = min;
            this.mMax = max;
            return true;
        }

        public boolean setSize(float size) {
            if (this.mSize == size) {
                return false;
            }
            this.mSize = size;
            return true;
        }

        public float convertToPoint(long value) {
            return (this.mSize * ((float) (value - this.mMin))) / ((float) (this.mMax - this.mMin));
        }

        public long convertToValue(float point) {
            return (long) (((float) this.mMin) + ((((float) (this.mMax - this.mMin)) * point) / this.mSize));
        }

        public long buildLabel(Resources res, SpannableStringBuilder builder, long value) {
            builder.replace(0, builder.length(), Long.toString(value));
            return value;
        }

        public float[] getTickPoints() {
            float[] ticks = new float[32];
            int i = 0;
            Time time = new Time();
            time.set(this.mMax);
            time.monthDay -= time.weekDay - FIRST_DAY_OF_WEEK;
            time.second = 0;
            time.minute = 0;
            time.hour = 0;
            time.normalize(true);
            for (long timeMillis = time.toMillis(true); timeMillis > this.mMin; timeMillis = time.toMillis(true)) {
                if (timeMillis <= this.mMax) {
                    int i2 = i + 1;
                    ticks[i] = convertToPoint(timeMillis);
                    i = i2;
                }
                time.monthDay -= 7;
                time.normalize(true);
            }
            return Arrays.copyOf(ticks, i);
        }

        public int shouldAdjustAxis(long value) {
            return 0;
        }
    }

    public ChartDataUsageView(Context context) {
        this(context, null, 0);
    }

    public ChartDataUsageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ChartDataUsageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.mVertListener = new OnSweepListener() {
            public void onSweep(ChartSweepView sweep, boolean sweepDone) {
                if (sweepDone) {
                    ChartDataUsageView.this.clearUpdateAxisDelayed(sweep);
                    ChartDataUsageView.this.updateEstimateVisible();
                    if (sweep == ChartDataUsageView.this.mSweepWarning && ChartDataUsageView.this.mListener != null) {
                        ChartDataUsageView.this.mListener.onWarningChanged();
                        return;
                    } else if (sweep == ChartDataUsageView.this.mSweepLimit && ChartDataUsageView.this.mListener != null) {
                        ChartDataUsageView.this.mListener.onLimitChanged();
                        return;
                    } else {
                        return;
                    }
                }
                ChartDataUsageView.this.sendUpdateAxisDelayed(sweep, false);
            }

            public void requestEdit(ChartSweepView sweep) {
                if (sweep == ChartDataUsageView.this.mSweepWarning && ChartDataUsageView.this.mListener != null) {
                    ChartDataUsageView.this.mListener.requestWarningEdit();
                } else if (sweep == ChartDataUsageView.this.mSweepLimit && ChartDataUsageView.this.mListener != null) {
                    ChartDataUsageView.this.mListener.requestLimitEdit();
                }
            }
        };
        init(new TimeAxis(), new InvertedChartAxis(new DataAxis()));
        this.mHandler = new Handler() {
            public void handleMessage(Message msg) {
                ChartSweepView sweep = msg.obj;
                ChartDataUsageView.this.updateVertAxisBounds(sweep);
                ChartDataUsageView.this.updateEstimateVisible();
                ChartDataUsageView.this.sendUpdateAxisDelayed(sweep, true);
            }
        };
    }

    protected void onFinishInflate() {
        super.onFinishInflate();
        this.mGrid = (ChartGridView) findViewById(R.id.grid);
        this.mSeries = (ChartNetworkSeriesView) findViewById(R.id.series);
        this.mDetailSeries = (ChartNetworkSeriesView) findViewById(R.id.detail_series);
        this.mDetailSeries.setVisibility(8);
        this.mSweepLimit = (ChartSweepView) findViewById(R.id.sweep_limit);
        this.mSweepWarning = (ChartSweepView) findViewById(R.id.sweep_warning);
        this.mSweepWarning.setValidRangeDynamic(null, this.mSweepLimit);
        this.mSweepLimit.setValidRangeDynamic(this.mSweepWarning, null);
        this.mSweepLimit.setNeighbors(this.mSweepWarning);
        this.mSweepWarning.setNeighbors(this.mSweepLimit);
        this.mSweepWarning.addOnSweepListener(this.mVertListener);
        this.mSweepLimit.addOnSweepListener(this.mVertListener);
        this.mSweepWarning.setDragInterval(5242880);
        this.mSweepLimit.setDragInterval(5242880);
        this.mGrid.init(this.mHoriz, this.mVert);
        this.mSeries.init(this.mHoriz, this.mVert);
        this.mDetailSeries.init(this.mHoriz, this.mVert);
        this.mSweepWarning.init(this.mVert);
        this.mSweepLimit.init(this.mVert);
        setActivated(false);
    }

    public void setListener(DataUsageChartListener listener) {
        this.mListener = listener;
    }

    public void bindNetworkStats(NetworkStatsHistory stats) {
        this.mSeries.bindNetworkStats(stats);
        this.mHistory = stats;
        updateVertAxisBounds(null);
        updateEstimateVisible();
        updatePrimaryRange();
        requestLayout();
    }

    public void bindDetailNetworkStats(NetworkStatsHistory stats) {
        this.mDetailSeries.bindNetworkStats(stats);
        this.mDetailSeries.setVisibility(stats != null ? 0 : 8);
        if (this.mHistory != null) {
            this.mDetailSeries.setEndTime(this.mHistory.getEnd());
        }
        updateVertAxisBounds(null);
        updateEstimateVisible();
        updatePrimaryRange();
        requestLayout();
    }

    public void bindNetworkPolicy(NetworkPolicy policy) {
        if (policy == null) {
            this.mSweepLimit.setVisibility(4);
            this.mSweepLimit.setValue(-1);
            this.mSweepWarning.setVisibility(4);
            this.mSweepWarning.setValue(-1);
            return;
        }
        if (policy.limitBytes != -1) {
            this.mSweepLimit.setVisibility(0);
            this.mSweepLimit.setEnabled(true);
            this.mSweepLimit.setValue(policy.limitBytes);
        } else {
            this.mSweepLimit.setVisibility(4);
            this.mSweepLimit.setEnabled(false);
            this.mSweepLimit.setValue(-1);
        }
        if (policy.warningBytes != -1) {
            this.mSweepWarning.setVisibility(0);
            this.mSweepWarning.setValue(policy.warningBytes);
        } else {
            this.mSweepWarning.setVisibility(4);
            this.mSweepWarning.setValue(-1);
        }
        updateVertAxisBounds(null);
        requestLayout();
        invalidate();
    }

    private void updateVertAxisBounds(ChartSweepView activeSweep) {
        long max = this.mVertMax;
        long newMax = 0;
        if (activeSweep != null) {
            int adjustAxis = activeSweep.shouldAdjustAxis();
            if (adjustAxis > 0) {
                newMax = (11 * max) / 10;
            } else if (adjustAxis < 0) {
                newMax = (9 * max) / 10;
            } else {
                newMax = max;
            }
        }
        newMax = Math.max(Math.max((Math.max(Math.max(this.mSeries.getMaxVisible(), this.mDetailSeries.getMaxVisible()), Math.max(this.mSweepWarning.getValue(), this.mSweepLimit.getValue())) * 12) / 10, 52428800), newMax);
        if (newMax != this.mVertMax) {
            this.mVertMax = newMax;
            boolean changed = this.mVert.setBounds(0, newMax);
            this.mSweepWarning.setValidRange(0, newMax);
            this.mSweepLimit.setValidRange(0, newMax);
            if (changed) {
                this.mSeries.invalidatePath();
                this.mDetailSeries.invalidatePath();
            }
            this.mGrid.invalidate();
            if (activeSweep != null) {
                activeSweep.updateValueFromPosition();
            }
            if (this.mSweepLimit != activeSweep) {
                layoutSweep(this.mSweepLimit);
            }
            if (this.mSweepWarning != activeSweep) {
                layoutSweep(this.mSweepWarning);
            }
        }
    }

    private void updateEstimateVisible() {
        long maxEstimate = this.mSeries.getMaxEstimate();
        long interestLine = Long.MAX_VALUE;
        if (this.mSweepWarning.isEnabled()) {
            interestLine = this.mSweepWarning.getValue();
        } else if (this.mSweepLimit.isEnabled()) {
            interestLine = this.mSweepLimit.getValue();
        }
        if (interestLine < 0) {
            interestLine = Long.MAX_VALUE;
        }
        this.mSeries.setEstimateVisible(maxEstimate >= (7 * interestLine) / 10);
    }

    private void sendUpdateAxisDelayed(ChartSweepView sweep, boolean force) {
        if (force || !this.mHandler.hasMessages(100, sweep)) {
            this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(100, sweep), 250);
        }
    }

    private void clearUpdateAxisDelayed(ChartSweepView sweep) {
        this.mHandler.removeMessages(100, sweep);
    }

    public boolean onTouchEvent(MotionEvent event) {
        if (isActivated()) {
            return false;
        }
        switch (event.getAction()) {
            case 0:
                return true;
            case 1:
                setActivated(true);
                return true;
            default:
                return false;
        }
    }

    public long getInspectStart() {
        return this.mInspectStart;
    }

    public long getInspectEnd() {
        return this.mInspectEnd;
    }

    public long getWarningBytes() {
        return this.mSweepWarning.getLabelValue();
    }

    public long getLimitBytes() {
        return this.mSweepLimit.getLabelValue();
    }

    public void setVisibleRange(long visibleStart, long visibleEnd) {
        boolean changed = this.mHoriz.setBounds(visibleStart, visibleEnd);
        this.mGrid.setBounds(visibleStart, visibleEnd);
        this.mSeries.setBounds(visibleStart, visibleEnd);
        this.mDetailSeries.setBounds(visibleStart, visibleEnd);
        this.mInspectStart = visibleStart;
        this.mInspectEnd = visibleEnd;
        requestLayout();
        if (changed) {
            this.mSeries.invalidatePath();
            this.mDetailSeries.invalidatePath();
        }
        updateVertAxisBounds(null);
        updateEstimateVisible();
        updatePrimaryRange();
    }

    private void updatePrimaryRange() {
        if (this.mDetailSeries.getVisibility() == 0) {
            this.mSeries.setSecondary(true);
        } else {
            this.mSeries.setSecondary(false);
        }
    }

    private static void setText(SpannableStringBuilder builder, Object key, CharSequence text, String bootstrap) {
        int start = builder.getSpanStart(key);
        int end = builder.getSpanEnd(key);
        if (start == -1) {
            start = TextUtils.indexOf(builder, bootstrap);
            end = start + bootstrap.length();
            builder.setSpan(key, start, end, 18);
        }
        builder.replace(start, end, text);
    }

    private static long roundUpToPowerOfTwo(long i) {
        i--;
        i |= i >>> 1;
        i |= i >>> 2;
        i |= i >>> 4;
        i |= i >>> 8;
        i |= i >>> 16;
        i = (i | (i >>> 32)) + 1;
        return i > 0 ? i : Long.MAX_VALUE;
    }
}
