import { NextResponse } from 'next/server'

export async function GET() {
  return NextResponse.json({
    applinks: {
      apps: [],
      details: [
        // Builds signed by the company team (A265CB68GF, after the App Store
        // transfer) and by the previous team (9T27H6JKJM, still on users'
        // phones until they update) both need universal links.
        {
          appIDs: ['A265CB68GF.az.tribe.lifeplanner', '9T27H6JKJM.az.tribe.lifeplanner'],
          components: [{ '/': '/*' }],
        },
        {
          appID: 'A265CB68GF.az.tribe.lifeplanner',
          paths: ['/*'],
        },
        {
          appID: '9T27H6JKJM.az.tribe.lifeplanner',
          paths: ['/*'],
        },
      ],
    },
  })
}
