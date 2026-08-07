# تطبيقات الإرسال والاشتراكات

## 1) تطبيق الإرسال `app` → `releases/bulk-sender.apk`
- مستقل للإرسال عبر Accessibility + أيقونة منبثقة
- يتطلب **رمز اشتراك** من الأدمن + رابط السيرفر
- اسحب الأيقونة لأسفل الشاشة لإغلاقها
- الأدمن يمكنه إيقاف الرمز فيتوقف التطبيق

## 2) تطبيق الأدمن `admin` → `releases/bulk-admin.apk`
- تسجيل دخول بحساب الأدمن على السيرفر
- إنشاء رموز اشتراك بلا انتهاء صلاحية
- تفعيل / إيقاف / فك ربط جهاز / حذف / نسخ الرمز

## بناء
```bash
export ANDROID_HOME=$HOME/android-sdk
cd android-app
./gradlew :app:assembleDebug :admin:assembleDebug
```

## سيرفر VPS
الـ APIs موجودة في السيرفر الحالي:
- `POST /api/license/activate`
- `POST /api/license/status`
- `GET/POST/PUT/DELETE /api/licenses` (أدمن فقط)
