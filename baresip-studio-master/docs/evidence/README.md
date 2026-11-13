# Evidence format

Text evidence is normalised to UTF-8, LF line endings and no trailing whitespace
for Git review. Message content, commands, outcomes and artifact hashes are
preserved. These files are not byte-identical captures of terminal streams.
Raw pre-publication copies remain in the ignored local recovery cache.

The final native build exits 0; APK inspection and separate assembly confirmation
exit 0. The combined clean Android/lint run exits 1 because lint reports 401
MissingTranslation errors and 100 warnings. See `../BUILD_VERIFICATION.md`.
