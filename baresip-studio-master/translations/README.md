# SWUSIP language support

The pilot ships the default English UI and the three upstream language packs with
complete resource coverage: Czech, Swedish and Simplified Chinese. All ordinary
UI strings remain translatable. No MissingTranslation check or locale is suppressed
in lint, and no ordinary UI labels were marked `translatable="false"`.

`pending/` preserves the 23 unfinished upstream locale files unchanged. Seven were
empty placeholders; others lacked between 2 and 399 of the 401 translatable
default strings. These files are outside Android's `res/` source set and are not
advertised as supported translations. On other device languages Android uses the
default English resources. Existing completed translations were retained.

This is a conservative pilot scope pending the project lead's language preference,
not a claim that the partial translations have been completed or reviewed. Native
speaker review remains required even for resource-complete upstream packs.

To enable another locale, complete its missing translations using the current
`values/strings.xml`, preserve format placeholders/markup, obtain language review,
then restore its file to `app/src/main/res/values-<locale>/strings.xml`. Run
`python scripts/audit_translations.py`, the resource regression tests and
`gradlew.bat :app:lintDebug`. Do not copy English values into translated packs to
make coverage appear complete. Original upstream licences/notices remain applicable.
