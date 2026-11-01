import os
import zipfile

def create_mac_zip():
    source_dir = "PimcoreAutoTool_v2.0_macOS"
    zip_path = "PimcoreAutoTool_v2.0_macOS.zip"
    
    if os.path.exists(zip_path):
        os.remove(zip_path)
        
    print(f"Creating {zip_path} with UNIX executable permissions (0755)...")
    with zipfile.ZipFile(zip_path, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as zf:
        for root, dirs, files in os.walk(source_dir):
            for file in files:
                full_path = os.path.join(root, file)
                rel_path = os.path.relpath(full_path, source_dir)
                
                # Normalize slashes for ZIP format
                arcname = rel_path.replace("\\", "/")
                
                zinfo = zipfile.ZipInfo(arcname)
                zinfo.compress_type = zipfile.ZIP_DEFLATED
                # UNIX system type = 3
                zinfo.create_system = 3
                
                # Check executable files
                is_executable = (
                    arcname.endswith(".command") or 
                    arcname.endswith(".sh") or 
                    "/bin/" in arcname or 
                    arcname.endswith("/java") or
                    arcname.endswith("/jspawnhelper")
                )
                
                if is_executable:
                    # -rwxr-xr-x (0755)
                    zinfo.external_attr = 0o100755 << 16
                else:
                    # -rw-r--r-- (0644)
                    zinfo.external_attr = 0o100644 << 16
                
                with open(full_path, "rb") as f:
                    zf.writestr(zinfo, f.read())
                    
    print(f"Done! {zip_path} size: {os.path.getsize(zip_path) / (1024*1024):.2f} MB")

if __name__ == "__main__":
    create_mac_zip()
