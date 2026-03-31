import { NextResponse } from 'next/server'

export async function GET() {
  return NextResponse.json({
    applinks: {
      apps: [],
      details: [
        {
          appID: '9T27H6JKJM.az.tribe.lifeplanner',
          paths: ['/*'],
        },
      ],
    },
  })
}
