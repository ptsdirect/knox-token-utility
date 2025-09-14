import knoxTokenLibrary from '@redredgroup/samsungknox-token-library';

(async () => {
  const { accessToken } = await knoxTokenLibrary.generateSignedAccessTokenJWT({
    credential: {
      // Provide either key or path. For file:
      path: 'credential.json',
      // Or for direct key:
      // key: 'credential',
    },
    accessToken: 'my-access-token', // Replace with your actual access token
  });

  console.log(accessToken);
})();
