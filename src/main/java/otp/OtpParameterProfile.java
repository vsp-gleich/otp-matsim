package otp;

enum OtpParameterProfile {
	
	Pt_and_ShortWalk (750, true, false),
	Pt_and_LongWalk (1500, true, false),
	Pt_and_ShortBike (1000, false, true),
	Pt_and_LongBike (3000, false, true),
	Bike_only (Double.POSITIVE_INFINITY, false, true);
	
	final double maxWalkDistance;
	final boolean walkAllowed;
	final boolean bikeAllowed;
	
	OtpParameterProfile(double maxWalkDistance, boolean walkAllowed, boolean bikeAllowed){
		this.maxWalkDistance = maxWalkDistance;
		this.walkAllowed = walkAllowed;
		this.bikeAllowed = bikeAllowed;
	}
}
