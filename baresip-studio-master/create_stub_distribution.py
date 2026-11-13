import os
import shutil

libs = [
    ("openssl", ["libcrypto.a", "libssl.a"]),
    ("re", ["libre.a"]),
    ("opus", ["libopus.a"]),
    ("g722", ["libg722.a"]),
    ("g7221", ["libg722_1.a"]),
    ("g729", ["libbcg729.a"]),
    ("ilbc", ["libilbc.a"]),
    ("codec2", ["libcodec2.a"]),
    ("amr", ["libamrnb.a", "libamrwb.a", "libamrwbenc.a"]),
    ("gzrtp", ["libzrtpcppcore.a"]),
    ("sndfile", ["libsndfile.a"]),
    ("baresip", ["libbaresip.a"])
]

arches = ["arm64-v8a", "armeabi-v7a", "x86_64"]
dist_dir = "distribution"
lib_android_dir = "libbaresip-android"

for lib_name, lib_files in libs:
    for arch in arches:
        lib_path = os.path.join(dist_dir, lib_name, "lib", arch)
        os.makedirs(lib_path, exist_ok=True)
        for f in lib_files:
            file_path = os.path.join(lib_path, f)
            with open(file_path, "wb") as file:
                file.write(b"!<arch>\n")

    # Copy actual include directory if it exists in libbaresip-android
    src_inc = os.path.join(lib_android_dir, lib_name, "include")
    dest_inc = os.path.join(dist_dir, lib_name, "include")
    if os.path.exists(src_inc):
        if os.path.exists(dest_inc):
            shutil.rmtree(dest_inc)
        shutil.copytree(src_inc, dest_inc)
    else:
        os.makedirs(dest_inc, exist_ok=True)
        with open(os.path.join(dest_inc, f"{lib_name}.h"), "w") as f:
            f.write(f"// Stub header for {lib_name}\n")

print("Stub distribution with real headers created successfully!")
