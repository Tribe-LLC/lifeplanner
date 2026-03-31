import { NextResponse } from 'next/server'

export async function GET() {
  return NextResponse.json([
    {
      relation: ['delegate_permission/common.handle_all_urls'],
      target: {
        namespace: 'android_app',
        package_name: 'az.tribe.lifeplanner',
        sha256_cert_fingerprints: [
          'F5:4C:4E:D4:57:9A:01:0E:D1:EB:5D:34:03:66:6E:06:81:6A:B5:B8:D3:B4:16:E9:B0:1D:A0:E9:19:68:EA:22',
          '21:81:E5:14:01:0C:13:55:4B:64:28:E2:7D:D8:D7:8A:7B:1D:97:D6:6F:93:E4:07:20:93:65:2D:F3:16:E0:0E',
        ],
      },
    },
  ])
}
