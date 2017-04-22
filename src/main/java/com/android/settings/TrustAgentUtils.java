package com.android.settings;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Slog;
import android.util.Xml;
import com.android.internal.R;

public class TrustAgentUtils {

    public static class TrustAgentComponentInfo {
        ComponentName componentName;
        String summary;
        String title;
    }

    public static boolean checkProvidePermission(ResolveInfo resolveInfo, PackageManager pm) {
        String packageName = resolveInfo.serviceInfo.packageName;
        if (pm.checkPermission("android.permission.PROVIDE_TRUST_AGENT", packageName) == 0) {
            return true;
        }
        Log.w("TrustAgentUtils", "Skipping agent because package " + packageName + " does not have permission " + "android.permission.PROVIDE_TRUST_AGENT" + ".");
        return false;
    }

    public static ComponentName getComponentName(ResolveInfo resolveInfo) {
        if (resolveInfo == null || resolveInfo.serviceInfo == null) {
            return null;
        }
        return new ComponentName(resolveInfo.serviceInfo.packageName, resolveInfo.serviceInfo.name);
    }

    public static TrustAgentComponentInfo getSettingsComponent(PackageManager pm, ResolveInfo resolveInfo) {
        if (resolveInfo == null || resolveInfo.serviceInfo == null || resolveInfo.serviceInfo.metaData == null) {
            return null;
        }
        String cn = null;
        TrustAgentComponentInfo trustAgentComponentInfo = new TrustAgentComponentInfo();
        XmlResourceParser parser = null;
        Exception caughtException = null;
        try {
            parser = resolveInfo.serviceInfo.loadXmlMetaData(pm, "android.service.trust.trustagent");
            if (parser == null) {
                Slog.w("TrustAgentUtils", "Can't find android.service.trust.trustagent meta-data");
                if (parser == null) {
                    return null;
                }
                parser.close();
                return null;
            }
            Resources res = pm.getResourcesForApplication(resolveInfo.serviceInfo.applicationInfo);
            AttributeSet attrs = Xml.asAttributeSet(parser);
            int type;
            do {
                type = parser.next();
                if (type == 1) {
                    break;
                }
            } while (type != 2);
            if ("trust-agent".equals(parser.getName())) {
                TypedArray sa = res.obtainAttributes(attrs, R.styleable.TrustAgent);
                trustAgentComponentInfo.summary = sa.getString(1);
                trustAgentComponentInfo.title = sa.getString(0);
                cn = sa.getString(2);
                sa.recycle();
                if (parser != null) {
                    parser.close();
                }
                if (caughtException != null) {
                    Slog.w("TrustAgentUtils", "Error parsing : " + resolveInfo.serviceInfo.packageName, caughtException);
                    return null;
                }
                if (cn != null && cn.indexOf(47) < 0) {
                    cn = resolveInfo.serviceInfo.packageName + "/" + cn;
                }
                trustAgentComponentInfo.componentName = cn == null ? null : ComponentName.unflattenFromString(cn);
                return trustAgentComponentInfo;
            }
            Slog.w("TrustAgentUtils", "Meta-data does not start with trust-agent tag");
            if (parser == null) {
                return null;
            }
            parser.close();
            return null;
        } catch (Exception e) {
            caughtException = e;
            if (parser != null) {
                parser.close();
            }
        } catch (Exception e2) {
            caughtException = e2;
            if (parser != null) {
                parser.close();
            }
        } catch (Exception e22) {
            caughtException = e22;
            if (parser != null) {
                parser.close();
            }
        } catch (Throwable th) {
            if (parser != null) {
                parser.close();
            }
        }
    }
}
