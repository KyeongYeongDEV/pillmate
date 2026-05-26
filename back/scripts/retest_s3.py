import boto3
import os
from dotenv import load_dotenv

load_dotenv()

access_key = os.getenv("AWS_ACCESS_KEY_ID")
secret_key = os.getenv("AWS_SECRET_ACCESS_KEY")
region = os.getenv("AWS_REGION", "ap-northeast-2")
bucket_name = os.getenv("S3_BUCKET_NAME", "pillmate-prescriptions")

print(f"--- S3 Connectivity Re-test ---")
print(f"Target Bucket: {bucket_name}")
print(f"Region: {region}")

try:
    # 1. Check Identity
    sts = boto3.client('sts', aws_access_key_id=access_key, aws_secret_access_key=secret_key, region_name=region)
    identity = sts.get_caller_identity()
    print(f"✅ Credentials Valid: {identity['Arn']}")

    # 2. Check Bucket Accessibility
    s3 = boto3.client('s3', aws_access_key_id=access_key, aws_secret_access_key=secret_key, region_name=region)
    
    print(f"Attempting to access '{bucket_name}'...")
    try:
        s3.head_bucket(Bucket=bucket_name)
        print(f"✅ Bucket '{bucket_name}' is accessible (HeadBucket).")
        
        # Try Listing
        objs = s3.list_objects_v2(Bucket=bucket_name, MaxKeys=1)
        print(f"✅ Can list objects in '{bucket_name}'.")
    except Exception as e:
        print(f"❌ Access to '{bucket_name}' failed: {e}")

    # 3. List all available buckets
    print("\nAvailable Buckets:")
    try:
        buckets = s3.list_buckets()
        for b in buckets['Buckets']:
            print(f"  - {b['Name']}")
    except Exception as e:
        print(f"❌ Could not list buckets: {e}")

except Exception as e:
    print(f"❌ Critical failure: {e}")
